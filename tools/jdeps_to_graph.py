#!/usr/bin/env python3
"""Turn the jdeps edge list into graph data for the mod.

Reads jdeps-output/class-nodes.tsv and jdeps-output/class-edges.tsv, made by
jdeps-minecraft.sh. Builds the undirected class graph, computes a spectral
embedding of its largest connected component, clusters that embedding with
k-means, and writes CSV files that any developer can open:

  graph-data/nodes.csv          one row per top-level class: dist, degree,
                                component, cluster and the first three spectral
                                coordinates e1, e2, e3 (empty outside the main
                                component)
  graph-data/clusters.csv       one row per cluster: size and its top packages
  graph-data/cluster-edges.csv  one row per cluster pair: number of class edges
                                between them (a row with cluster_a == cluster_b
                                counts edges inside that cluster)

The spectral coordinates are the eigenvectors of the normalized Laplacian,
rescaled so each axis has unit length. They place neighbors close together but
most classes land in a small central blob, so they are a starting point for a
layout, not final block positions. The script prints span and gap statistics
for each axis so that distortion can be judged.

Requires numpy only. Usage:

  python3 tools/jdeps_to_graph.py [--k 40] [--dims 8] [--raw-laplacian]

Written by Claude Code; conversation transcript: llm/transcripts/transcript_2026-10-16_5c2c690e-3be6-49dd-9724-3b112e8d00c0.md
"""
from __future__ import annotations

import argparse
import collections
import csv
import pathlib
import time

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parent.parent


def package(name: str, depth: int | None = None) -> str:
    parts = name.split(".")[:-1]
    if depth is not None:
        parts = parts[:depth]
    return ".".join(parts)


def read_inputs(in_dir: pathlib.Path):
    nodes: dict[str, str] = {}
    with open(in_dir / "class-nodes.tsv") as f:
        for line in f:
            name, dist = line.rstrip("\n").split("\t")
            nodes[name] = dist
    edges: set[tuple[str, str]] = set()
    with open(in_dir / "class-edges.tsv") as f:
        for line in f:
            a, b = line.rstrip("\n").split("\t")
            if a != b:
                edges.add((a, b))
                nodes.setdefault(a, "common")
                nodes.setdefault(b, "common")
    return nodes, edges


def components(n: int, adj: list[set[int]]) -> list[int]:
    comp = [-1] * n
    next_id = 0
    for s in range(n):
        if comp[s] != -1:
            continue
        stack = [s]
        comp[s] = next_id
        while stack:
            u = stack.pop()
            for v in adj[u]:
                if comp[v] == -1:
                    comp[v] = next_id
                    stack.append(v)
        next_id += 1
    return comp


def spectral_embedding(adj: list[set[int]], members: list[int], dims: int, raw: bool):
    """Return (coords[m, dims], laplacian eigenvalues[dims + 1]) for the given nodes."""
    sub = {old: new for new, old in enumerate(members)}
    m = len(members)
    W = np.zeros((m, m), dtype=np.float32)
    for u in members:
        for v in adj[u]:
            W[sub[u], sub[v]] = 1.0
    deg = W.sum(axis=1)
    t0 = time.time()
    if raw:
        L = np.diag(deg) - W
        vals, vecs = np.linalg.eigh(L)
        lap_vals = vals[: dims + 1]
        X = vecs[:, 1 : dims + 1]
    else:
        d_inv_sqrt = 1.0 / np.sqrt(deg)
        N = (W * d_inv_sqrt[:, None]) * d_inv_sqrt[None, :]
        del W
        vals, vecs = np.linalg.eigh(N)
        lap_vals = 1.0 - vals[::-1][: dims + 1]
        X = vecs[:, ::-1][:, 1 : dims + 1] * d_inv_sqrt[:, None]
    print(f"eigh on {m}x{m}: {time.time() - t0:.1f}s", flush=True)
    X = X / np.linalg.norm(X, axis=0, keepdims=True)
    return X.astype(np.float64), lap_vals, deg


def kmeans(X: np.ndarray, k: int, iters: int = 100, seed: int = 0) -> np.ndarray:
    rng = np.random.default_rng(seed)
    centers = [X[rng.integers(len(X))]]
    for _ in range(1, k):
        d2 = np.min(((X[:, None, :] - np.array(centers)[None, :, :]) ** 2).sum(-1), axis=1)
        centers.append(X[rng.choice(len(X), p=d2 / d2.sum())])
    C = np.array(centers)
    for _ in range(iters):
        lab = np.argmin(((X[:, None, :] - C[None, :, :]) ** 2).sum(-1), axis=1)
        newC = np.array([X[lab == j].mean(0) if np.any(lab == j) else C[j] for j in range(k)])
        if np.allclose(newC, C):
            break
        C = newC
    return lab


def axis_stats(X: np.ndarray) -> None:
    """Print how wide and how crowded each of the first three axes is."""
    print("\nper-axis statistics (coordinates scaled so each axis has unit length):")
    print("  axis  span       central 1%  min gap     span/min gap")
    for k in range(min(3, X.shape[1])):
        col = X[:, k]
        span = col.max() - col.min()
        med = np.median(col)
        central = np.mean(np.abs(col - med) <= 0.005 * span)
        s = np.sort(np.unique(col))
        gaps = np.diff(s)
        min_gap = gaps[gaps > 0].min()
        print(f"  e{k + 1}    {span:.5f}    {central:6.1%}      {min_gap:.2e}    {span / min_gap:.3g}")
    print("  'central 1%' = share of classes within +-0.5% of the span around the median.")
    print("  'span/min gap' = world width in blocks along that axis if the two closest")
    print("  classes on the axis were placed one block apart.")

    P = X[:, :3]
    n = len(P)
    nn = np.full(n, np.inf)
    step = 1024
    for i in range(0, n, step):
        d2 = ((P[i : i + step, None, :] - P[None, :, :]) ** 2).sum(-1)
        rows = np.arange(min(step, n - i))
        d2[rows, rows + i] = np.inf
        nn[i : i + step] = np.sqrt(d2.min(axis=1))
    nn = nn[np.isfinite(nn) & (nn > 0)]
    extent = np.sqrt(((P.max(0) - P.min(0)) ** 2).sum())
    print(f"\n3D (e1, e2, e3): diagonal extent={extent:.4f}  nearest-neighbor distance "
          f"min={nn.min():.2e} median={np.median(nn):.2e} max={nn.max():.2e}")
    print(f"  blocks across the diagonal if the closest pair sits one block apart: {extent / nn.min():.3g}")
    print(f"  same, if the *median* pair sits one block apart: {extent / np.median(nn):.3g}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--in-dir", default=ROOT / "jdeps-output", type=pathlib.Path)
    ap.add_argument("--out-dir", default=ROOT / "graph-data", type=pathlib.Path)
    ap.add_argument("--k", type=int, default=40, help="number of k-means clusters")
    ap.add_argument("--dims", type=int, default=8, help="eigenvectors used for clustering")
    ap.add_argument("--raw-laplacian", action="store_true",
                    help="use the unnormalized Laplacian (hubs dominate; for comparison only)")
    ap.add_argument("--seed", type=int, default=0)
    args = ap.parse_args()

    nodes, edges = read_inputs(args.in_dir)
    names = sorted(nodes)
    idx = {nm: i for i, nm in enumerate(names)}
    n = len(names)
    adj: list[set[int]] = [set() for _ in range(n)]
    for a, b in edges:
        adj[idx[a]].add(idx[b])
        adj[idx[b]].add(idx[a])
    degree = [len(s) for s in adj]
    comp = components(n, adj)
    comp_sizes = collections.Counter(comp)
    main_comp, main_size = comp_sizes.most_common(1)[0]
    orphans = sum(1 for d in degree if d == 0)
    print(f"nodes={n} directed edges={len(edges)} components={len(comp_sizes)} "
          f"largest={main_size} orphans (degree 0)={orphans}", flush=True)

    members = [i for i in range(n) if comp[i] == main_comp]
    X, lap_vals, deg = spectral_embedding(adj, members, args.dims, args.raw_laplacian)
    print("smallest Laplacian eigenvalues:", np.round(lap_vals, 4))
    axis_stats(X)

    Xn = X / np.maximum(np.linalg.norm(X, axis=1, keepdims=True), 1e-12)
    lab = kmeans(Xn, args.k, seed=args.seed)
    cluster = [-1] * n
    coords: list[tuple[float, float, float] | None] = [None] * n
    for row, i in enumerate(members):
        cluster[i] = int(lab[row])
        coords[i] = (X[row, 0], X[row, 1], X[row, 2])

    keys3 = [package(names[i], 3) for i in members]
    purity = 0
    for j in range(args.k):
        in_j = [keys3[row] for row in range(len(members)) if lab[row] == j]
        if in_j:
            purity += collections.Counter(in_j).most_common(1)[0][1]
    print(f"\nk-means k={args.k}: purity against depth-3 packages = {purity / len(members):.3f}")

    args.out_dir.mkdir(parents=True, exist_ok=True)
    with open(args.out_dir / "nodes.csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["class", "dist", "degree", "component", "cluster", "e1", "e2", "e3"])
        for i, nm in enumerate(names):
            c = coords[i]
            w.writerow([nm, nodes[nm], degree[i], comp[i], cluster[i]]
                       + ([f"{c[0]:.6f}", f"{c[1]:.6f}", f"{c[2]:.6f}"] if c else ["", "", ""]))

    with open(args.out_dir / "clusters.csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["cluster", "size", "package_1", "share_1", "package_2", "share_2", "package_3", "share_3"])
        for j in range(args.k):
            in_j = [package(names[i], 4) for i in members if cluster[i] == j]
            top = collections.Counter(in_j).most_common(3)
            row = [j, len(in_j)]
            for p, c in top:
                row += [p, f"{c / len(in_j):.2f}"]
            row += [""] * (8 - len(row))
            w.writerow(row)

    pair_edges: collections.Counter[tuple[int, int]] = collections.Counter()
    for a, b in edges:
        ca, cb = cluster[idx[a]], cluster[idx[b]]
        if ca == -1 or cb == -1:
            continue
        pair_edges[(min(ca, cb), max(ca, cb))] += 1
    with open(args.out_dir / "cluster-edges.csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["cluster_a", "cluster_b", "edges"])
        for (a, b), c in sorted(pair_edges.items()):
            w.writerow([a, b, c])
    print(f"wrote {args.out_dir / 'nodes.csv'}, clusters.csv, cluster-edges.csv")


if __name__ == "__main__":
    main()
