package us.drullk.graphmachine.client.render;

import org.joml.Vector3f;

/**
 * Finds the node under a ray with a slab test against every visible node's cube. 6,700 boxes take well
 * under a millisecond, so this runs every frame.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphPicker {
    /** Nodes farther than this many blocks are not picked. */
    private static final float MAX_DISTANCE = 512.0F;

    private GraphPicker() {
    }

    /**
     * The compacted index of the nearest node whose cube the ray hits, or -1.
     *
     * @param boxes four floats per node: x, y, z, edge length (from {@link GraphView#nodeBoxes()})
     * @param count number of nodes in {@code boxes}
     */
    public static int pick(float[] boxes, int count, double originX, double originY, double originZ, Vector3f direction) {
        float invX = 1.0F / direction.x;
        float invY = 1.0F / direction.y;
        float invZ = 1.0F / direction.z;
        float best = MAX_DISTANCE;
        int bestIndex = -1;
        for (int i = 0; i < count; i++) {
            float half = boxes[i * 4 + 3] * 0.5F;
            float minX = (float) (boxes[i * 4] - half - originX);
            float minY = (float) (boxes[i * 4 + 1] - half - originY);
            float minZ = (float) (boxes[i * 4 + 2] - half - originZ);
            float maxX = minX + half * 2.0F;
            float maxY = minY + half * 2.0F;
            float maxZ = minZ + half * 2.0F;

            float t1 = minX * invX;
            float t2 = maxX * invX;
            float near = Math.min(t1, t2);
            float far = Math.max(t1, t2);
            t1 = minY * invY;
            t2 = maxY * invY;
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
            t1 = minZ * invZ;
            t2 = maxZ * invZ;
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));

            if (far >= Math.max(near, 0.0F) && near < best) {
                best = Math.max(near, 0.0F);
                bestIndex = i;
            }
        }
        return bestIndex;
    }
}
