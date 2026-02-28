package com.daqem.grieflogger.model.home;

import net.minecraft.world.level.ChunkPos;

import java.util.*;

/**
 * Flood-fill clustering of chunk positions.
 * Groups chunks that are within maxDistance of each other into clusters.
 */
public class HomeCluster {

    private static final int MAX_NEIGHBOR_DISTANCE = 2;

    /**
     * Result of clustering: center chunk, radius, and all member chunks.
     */
    public record Cluster(ChunkPos center, int radius, List<ChunkPos> chunks) {
        public int size() {
            return chunks.size();
        }
    }

    /**
     * Cluster a list of chunk positions using flood-fill on a grid.
     * Chunks within MAX_NEIGHBOR_DISTANCE of each other are grouped.
     * Returns clusters sorted by size (largest first).
     */
    public static List<Cluster> cluster(List<ChunkPos> points) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }

        // Deduplicate
        Set<ChunkPos> remaining = new HashSet<>(points);
        List<Cluster> clusters = new ArrayList<>();

        while (!remaining.isEmpty()) {
            ChunkPos seed = remaining.iterator().next();
            List<ChunkPos> clusterChunks = new ArrayList<>();
            Queue<ChunkPos> queue = new LinkedList<>();

            queue.add(seed);
            remaining.remove(seed);

            while (!queue.isEmpty()) {
                ChunkPos current = queue.poll();
                clusterChunks.add(current);

                // Find neighbors within distance
                Iterator<ChunkPos> it = remaining.iterator();
                while (it.hasNext()) {
                    ChunkPos candidate = it.next();
                    if (Math.abs(candidate.x - current.x) <= MAX_NEIGHBOR_DISTANCE
                            && Math.abs(candidate.z - current.z) <= MAX_NEIGHBOR_DISTANCE) {
                        queue.add(candidate);
                        it.remove();
                    }
                }
            }

            if (!clusterChunks.isEmpty()) {
                clusters.add(buildCluster(clusterChunks));
            }
        }

        // Sort by size descending
        clusters.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return clusters;
    }

    /**
     * Build a Cluster from a list of chunk positions.
     * Center = average position, Radius = max Chebyshev distance from center.
     */
    private static Cluster buildCluster(List<ChunkPos> chunks) {
        long sumX = 0, sumZ = 0;
        for (ChunkPos c : chunks) {
            sumX += c.x;
            sumZ += c.z;
        }
        int centerX = (int) Math.round((double) sumX / chunks.size());
        int centerZ = (int) Math.round((double) sumZ / chunks.size());
        ChunkPos center = new ChunkPos(centerX, centerZ);

        int radius = 2; // minimum radius
        for (ChunkPos c : chunks) {
            int dist = Math.max(Math.abs(c.x - centerX), Math.abs(c.z - centerZ));
            if (dist > radius) {
                radius = dist;
            }
        }

        return new Cluster(center, radius, chunks);
    }
}
