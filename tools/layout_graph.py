#!/usr/bin/env python3
"""Give every class a block-scale position with a force-directed layout.

Reads graph-data/nodes.csv (made by tools/jdeps_to_graph.py) and
jdeps-output/class-edges.tsv (made by jdeps-minecraft.sh) and writes
graph-data/layout.csv with one row per class: class, x, y, z in block units.
In Minecraft y points up, so the spectral axes map as e1 -> x, e2 -> z and
e3 -> y (vertical).

The layout runs in two levels so that clusters stay readable clumps:

  1. Cluster level. Each of the k-means clusters becomes a disc whose radius
     grows with its member count. The discs repel each other when they touch
     and attract each other along graph-data/cluster-edges.csv, weighted by the
     number of class edges. Their start positions come from the mean spectral
     coordinates of their members.
  2. Class level. Every class starts near its cluster center, placed by the
     rank of its spectral coordinates inside the cluster plus a little jitter,
     because many classes share identical spectral points. Three forces act in
     each step: a spring along every class edge, a short-range repulsion
     between any two classes closer than a cutoff (found with a uniform grid,
     so the step is linear in the node count), and a gravity well that keeps
     a class inside its cluster disc. Vertical motion is damped, so the result
     is mostly a plane with gentle hills.

Classes with no edges (cluster -1) do not take part in the simulation. They
sit on a ring just outside the main layout.

The script prints the extent of the layout and the minimum and median
nearest-neighbor distance so that overlaps can be judged. It needs numpy only.
Usage:

  python3 tools/layout_graph.py [--spacing 1.5] [--iters 300] [--seed 0]

Written by Claude Code; conversation transcript: llm/transcripts/transcript_2026-10-16_08-53_a4074a22-803e-4cab-b75b-610f2553258e.md
"""
from __future__ import annotations

import argparse
import csv
import math
import pathlib
import time

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parent.parent

# Offsets of the 27 grid cells around and including a cell.
OFFSETS = np.array([(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1)])


def read_nodes(path: pathlib.Path):
    """Return class names, cluster ids and spectral coordinates.

    Spectral coordinates are NaN for classes outside the main component.
    """
    names: list[str] = []
    clusters: list[int] = []
    spectral: list[tuple[float, float, float]] = []
    with open(path, newline="") as f:
        for row in csv.DictReader(f):
            names.append(row["class"])
            clusters.append(int(row["cluster"]))
            coords = tuple(float(row[k]) if row[k] else math.nan for k in ("e1", "e2", "e3"))
            spectral.append(coords)
    return names, np.array(clusters), np.array(spectral)


def read_edges(path: pathlib.Path, index: dict[str, int]) -> np.ndarray:
    """Return an (m, 2) array of undirected class edges by node index."""
    pairs: set[tuple[int, int]] = set()
    with open(path) as f:
        for line in f:
            a, b = line.rstrip("\n").split("\t")
            if a == b or a not in index or b not in index:
                continue
            i, j = index[a], index[b]
            pairs.add((min(i, j), max(i, j)))
    return np.array(sorted(pairs), dtype=np.int64)


def read_cluster_edges(path: pathlib.Path, k: int) -> np.ndarray:
    """Return a symmetric (k, k) matrix of class-edge counts between clusters."""
    W = np.zeros((k, k))
    with open(path, newline="") as f:
        for row in csv.DictReader(f):
            a, b, w = int(row["cluster_a"]), int(row["cluster_b"]), float(row["edges"])
            if a != b:
                W[a, b] += w
                W[b, a] += w
    return W


def rank_scale(values: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """Map values to [-1, 1] by rank. Ties are broken at random."""
    noise = rng.random(len(values)) * 1e-12
    order = np.argsort(values + noise, kind="stable")
    ranks = np.empty(len(values))
    ranks[order] = np.arange(len(values))
    if len(values) < 2:
        return np.zeros(len(values))
    return ranks / (len(values) - 1) * 2 - 1


def layout_clusters(sizes: np.ndarray, W: np.ndarray, seeds: np.ndarray, spacing: float,
                    iters: int, rng: np.random.Generator) -> tuple[np.ndarray, np.ndarray]:
    """Place cluster centers in the ground plane. Returns (centers, radii).

    Each cluster is a disc with room for its members at the given spacing.
    Discs push apart when they overlap and pull together along weighted edges.
    """
    k = len(sizes)
    radii = spacing * np.sqrt(sizes / math.pi) * 1.25
    # Start from the spectral means, stretched so the discs roughly fit.
    P = seeds - seeds.mean(axis=0)
    P /= np.abs(P).max() + 1e-12
    P *= radii.sum() / 2
    P += rng.normal(scale=spacing, size=P.shape)
    weight = np.sqrt(W / W.max())
    gap = 2 * spacing
    for it in range(iters):
        temp = radii.max() * (1 - it / iters) + 0.05
        delta = P[:, None, :] - P[None, :, :]
        dist = np.linalg.norm(delta, axis=2) + 1e-9
        unit = delta / dist[:, :, None]
        np.fill_diagonal(dist, 1e9)
        touch = radii[:, None] + radii[None, :] + gap
        overlap = np.clip(touch - dist, 0, None)
        # Overlap pushes hard; any pair pushes a little so the discs do not collapse.
        rep = overlap * 2.0 + (touch / dist) ** 2 * spacing * 0.2
        # The pull saturates, so a well-connected cluster cannot drag its neighbors on top of itself.
        att = weight * np.clip(dist - touch, 0, 4 * spacing) * 0.5
        force = ((rep - att)[:, :, None] * unit).sum(axis=1)
        # A weak pull to the origin keeps the layout compact.
        force -= P * 0.02
        norm = np.linalg.norm(force, axis=1, keepdims=True) + 1e-9
        P += force / norm * np.minimum(norm, temp)
    # Push any discs that still overlap apart until every pair is clear.
    for _ in range(1000):
        delta = P[:, None, :] - P[None, :, :]
        dist = np.linalg.norm(delta, axis=2) + 1e-9
        np.fill_diagonal(dist, np.inf)
        overlap = np.clip(radii[:, None] + radii[None, :] + gap - dist, 0, None)
        if overlap.max() <= 0:
            break
        P += ((overlap * 0.55 / dist)[:, :, None] * delta).sum(axis=1)
    P -= P.mean(axis=0)
    return P, radii


def grid_pairs(P: np.ndarray, cell: float) -> tuple[np.ndarray, np.ndarray]:
    """Return index arrays (i, j), i < j, for all node pairs in the same or an adjacent grid cell."""
    c = np.floor(P / cell).astype(np.int64)
    c -= c.min(axis=0) - 1
    dims = c.max(axis=0) + 2
    key = (c[:, 0] * dims[1] + c[:, 1]) * dims[2] + c[:, 2]
    order = np.argsort(key, kind="stable")
    sorted_key = key[order]
    cells, start, count = np.unique(sorted_key, return_index=True, return_counts=True)
    node = np.arange(len(P))
    out_i = []
    out_j = []
    for dx, dy, dz in OFFSETS:
        nkey = key + (dx * dims[1] + dy) * dims[2] + dz
        pos = np.searchsorted(cells, nkey)
        pos = np.minimum(pos, len(cells) - 1)
        hit = cells[pos] == nkey
        if not hit.any():
            continue
        s = start[pos[hit]]
        n = count[pos[hit]]
        i = np.repeat(node[hit], n)
        offs = np.arange(n.sum()) - np.repeat(np.cumsum(n) - n, n)
        j = order[np.repeat(s, n) + offs]
        keep = i < j
        out_i.append(i[keep])
        out_j.append(j[keep])
    return np.concatenate(out_i), np.concatenate(out_j)


def layout_classes(P: np.ndarray, edges: np.ndarray, center: np.ndarray, radius: np.ndarray,
                   spacing: float, iters: int, vscale: float, gravity: float) -> np.ndarray:
    """Run the class-level simulation in place and return the positions.

    P is (n, 3) with y vertical. center and radius are per node (the node's
    cluster disc). Each step moves a node by a fixed fraction of the net
    force, capped by a temperature that cools over the run, so nodes stop
    once the forces balance.
    """
    n = len(P)
    cutoff = 2.5 * spacing
    ei, ej = edges[:, 0], edges[:, 1]
    degree = np.bincount(ei, minlength=n) + np.bincount(ej, minlength=n)
    # Springs to a hub pull less, so a hub does not crush its neighbors into a ball.
    hub = 1.0 / ((degree[ei] + 1.0) * (degree[ej] + 1.0)) ** 0.25
    damp = np.array([1.0, vscale, 1.0])
    step = 0.2
    for it in range(iters):
        progress = it / iters
        temp = spacing * (3.0 * (1 - progress) ** 2 + 0.02)
        force = np.zeros((n, 3))

        # Springs along edges. Their pull is linear in distance and capped.
        d = P[ej] - P[ei]
        dist = np.linalg.norm(d, axis=1, keepdims=True) + 1e-9
        pull = np.minimum(dist / spacing, 4.0) * hub[:, None]
        f = d / dist * pull
        for axis in range(3):
            force[:, axis] += np.bincount(ei, f[:, axis], minlength=n)
            force[:, axis] -= np.bincount(ej, f[:, axis], minlength=n)

        # Short-range repulsion between any two nodes within the cutoff.
        i, j = grid_pairs(P, cutoff)
        d = P[j] - P[i]
        dist = np.linalg.norm(d, axis=1)
        near = dist < cutoff
        i, j, d, dist = i[near], j[near], d[near], np.maximum(dist[near], 0.05)
        push = (spacing / dist) ** 2 - (spacing / cutoff) ** 2
        f = d / dist[:, None] * push[:, None] * 1.5
        for axis in range(3):
            force[:, axis] -= np.bincount(i, f[:, axis], minlength=n)
            force[:, axis] += np.bincount(j, f[:, axis], minlength=n)

        # Gravity toward the cluster center, with a soft wall at the disc edge.
        d = center - P
        flat = np.linalg.norm(d[:, [0, 2]], axis=1, keepdims=True) + 1e-9
        force += d * (gravity * flat / radius[:, None] + 2.0 * np.clip(flat - radius[:, None], 0, None)) / flat

        force *= damp * step
        norm = np.linalg.norm(force, axis=1, keepdims=True) + 1e-9
        P += force / norm * np.minimum(norm, temp)
        if it % 50 == 0 or it == iters - 1:
            print(f"  step {it:4d}  temp {temp:5.2f}  mean move {np.minimum(norm, temp).mean():.3f}", flush=True)
    return P


def separate(P: np.ndarray, min_dist: float, passes: int) -> int:
    """Push apart every pair closer than min_dist until none is left. Returns passes used.

    This is a position-based pass: both nodes of a pair move half the missing
    distance away from each other. It changes the layout only locally.
    """
    for p in range(passes):
        i, j = grid_pairs(P, min_dist)
        d = P[j] - P[i]
        dist = np.linalg.norm(d, axis=1)
        close = dist < min_dist - 1e-3
        if not close.any():
            return p
        i, j, d, dist = i[close], j[close], d[close], np.maximum(dist[close], 1e-6)
        shift = d / dist[:, None] * ((min_dist - dist) * 0.55)[:, None]
        for axis in range(3):
            P[:, axis] -= np.bincount(i, shift[:, axis], minlength=len(P))
            P[:, axis] += np.bincount(j, shift[:, axis], minlength=len(P))
    return passes


def nearest_neighbor(P: np.ndarray, chunk: int = 512) -> np.ndarray:
    """Return the distance from every point to its nearest other point."""
    out = np.empty(len(P))
    for s in range(0, len(P), chunk):
        block = P[s:s + chunk]
        d = np.linalg.norm(block[:, None, :] - P[None, :, :], axis=2)
        d[np.arange(len(block)), np.arange(s, s + len(block))] = np.inf
        out[s:s + chunk] = d.min(axis=1)
    return out


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--nodes", default=ROOT / "graph-data" / "nodes.csv", type=pathlib.Path)
    ap.add_argument("--edges", default=ROOT / "jdeps-output" / "class-edges.tsv", type=pathlib.Path)
    ap.add_argument("--cluster-edges", default=ROOT / "graph-data" / "cluster-edges.csv", type=pathlib.Path)
    ap.add_argument("--out", default=ROOT / "graph-data" / "layout.csv", type=pathlib.Path)
    ap.add_argument("--spacing", type=float, default=1.5, help="target distance between neighbors in blocks")
    ap.add_argument("--iters", type=int, default=300, help="class-level simulation steps")
    ap.add_argument("--cluster-iters", type=int, default=400, help="cluster-level simulation steps")
    ap.add_argument("--vscale", type=float, default=0.25, help="factor on vertical motion (1 = full 3D)")
    ap.add_argument("--height", type=float, default=8.0, help="vertical spread of the seed positions in blocks")
    ap.add_argument("--gravity", type=float, default=0.8, help="pull toward the cluster center")
    ap.add_argument("--min-dist", type=float, default=1.0, help="no two classes end closer than this")
    ap.add_argument("--seed", type=int, default=0)
    args = ap.parse_args()
    rng = np.random.default_rng(args.seed)
    t0 = time.time()

    names, cluster, spectral = read_nodes(args.nodes)
    index = {name: i for i, name in enumerate(names)}
    edges = read_edges(args.edges, index)
    active = cluster >= 0
    k = int(cluster.max()) + 1
    print(f"{len(names)} classes, {len(edges)} edges, {k} clusters, {(~active).sum()} isolated")

    # Level 1: cluster discs.
    sizes = np.bincount(cluster[active], minlength=k).astype(float)
    seeds = np.array([spectral[cluster == c][:, [0, 1]].mean(axis=0) for c in range(k)])
    W = read_cluster_edges(args.cluster_edges, k)
    centers2d, radii = layout_clusters(sizes, W, seeds, args.spacing, args.cluster_iters, rng)
    # Cluster height follows the rank of its mean e3, kept small.
    mean_e3 = np.array([spectral[cluster == c][:, 2].mean() for c in range(k)])
    center_y = rank_scale(mean_e3, rng) * args.height * 0.5
    centers = np.column_stack([centers2d[:, 0], center_y, centers2d[:, 1]])
    dist = np.linalg.norm(centers2d[:, None, :] - centers2d[None, :, :], axis=2)
    np.fill_diagonal(dist, np.inf)
    overlap = np.clip(radii[:, None] + radii[None, :] - dist, 0, None)
    print(f"cluster level done in {time.time() - t0:.1f}s; disc radii {radii.min():.1f}..{radii.max():.1f}; "
          f"{(overlap > 0).sum() // 2} overlapping disc pairs, deepest {overlap.max():.1f} blocks")

    # Level 2: seed classes inside their disc by spectral rank, then simulate.
    n = len(names)
    P = np.zeros((n, 3))
    for c in range(k):
        members = np.flatnonzero(cluster == c)
        sub = spectral[members]
        u = rank_scale(sub[:, 0], rng)
        v = rank_scale(sub[:, 1], rng)
        w = rank_scale(sub[:, 2], rng)
        # Squeeze the square of ranks into the disc.
        scale = radii[c] / math.sqrt(2) * 0.8
        P[members, 0] = centers[c, 0] + u * scale
        P[members, 2] = centers[c, 2] + v * scale
        P[members, 1] = centers[c, 1] + w * args.height * 0.5
    P[active] += rng.normal(scale=args.spacing * 0.1, size=(active.sum(), 3))

    act = np.flatnonzero(active)
    remap = -np.ones(n, dtype=np.int64)
    remap[act] = np.arange(len(act))
    sub_edges = remap[edges]
    sub_edges = sub_edges[(sub_edges >= 0).all(axis=1)]
    print("class level:")
    Pa = layout_classes(P[act].copy(), sub_edges, centers[cluster[act]], radii[cluster[act]],
                        args.spacing, args.iters, args.vscale, args.gravity)
    # A hair above the limit keeps pairs that sit right at it from trading places forever.
    used = separate(Pa, args.min_dist * 1.02, 200)
    P[act] = Pa
    print(f"class level done in {time.time() - t0:.1f}s; {used} separation passes")

    # Isolated classes go on a ring outside the main layout.
    iso = np.flatnonzero(~active)
    if len(iso):
        flat = Pa[:, [0, 2]] - Pa[:, [0, 2]].mean(axis=0)
        ring = np.linalg.norm(flat, axis=1).max() + 6 * args.spacing
        angle = np.linspace(0, 2 * math.pi, len(iso), endpoint=False)
        P[iso, 0] = Pa[:, 0].mean() + ring * np.cos(angle)
        P[iso, 2] = Pa[:, 2].mean() + ring * np.sin(angle)
        P[iso, 1] = 0.0

    # Center the whole layout on the origin of the ground plane.
    P[:, 0] -= Pa[:, 0].mean()
    P[:, 2] -= Pa[:, 2].mean()

    with open(args.out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["class", "x", "y", "z"])
        for name, (x, y, z) in zip(names, P):
            w.writerow([name, f"{x:.3f}", f"{y:.3f}", f"{z:.3f}"])
    out = args.out.relative_to(ROOT) if args.out.is_relative_to(ROOT) else args.out
    print(f"wrote {out}")

    lo, hi = P.min(axis=0), P.max(axis=0)
    print(f"extent x {lo[0]:.1f}..{hi[0]:.1f}  y {lo[1]:.1f}..{hi[1]:.1f}  z {lo[2]:.1f}..{hi[2]:.1f}")
    nn = nearest_neighbor(Pa)
    print(f"nearest neighbor (main layout): min {nn.min():.3f}  median {np.median(nn):.3f}  "
          f"below 1 block: {(nn < 1).sum()}")
    print(f"total {time.time() - t0:.1f}s")


if __name__ == "__main__":
    main()
