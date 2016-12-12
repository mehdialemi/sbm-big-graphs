package ir.ac.sbu.graph.ktruss.parallel;

import ir.ac.sbu.graph.Edge;
import ir.ac.sbu.graph.utils.VertexCompare;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 *
 */
public class ParallelKTruss6 extends ParallelKTrussBase {

    public static final int INT_SIZE = 4;
    private final ForkJoinPool forkJoinPool;

    public ParallelKTruss6(Edge[] edges, int minSup, int threads) {
        super(edges, minSup, threads);
        forkJoinPool = new ForkJoinPool(threads);
    }

    @Override
    public void start() throws Exception {
        long tStart = System.currentTimeMillis();
        batchSelector = new AtomicInteger(0);
        int maxVertexNum = 0;
        for (Edge edge : edges) {
            if (edge.v1 > maxVertexNum)
                maxVertexNum = edge.v1;
            if (edge.v2 > maxVertexNum)
                maxVertexNum = edge.v2;
        }

        long tMax = System.currentTimeMillis();
        System.out.println("find maxVertexNum in " + (tMax - tStart) + " ms");

        // Construct degree arrayList such that vertexId is the index of the arrayList.
        final int vCount = maxVertexNum + 1;
        int[] d = new int[vCount];  // vertex degree
        for (Edge e : edges) {
            d[e.v1]++;
            d[e.v2]++;
        }
        long t2 = System.currentTimeMillis();
        System.out.println("find degrees in " + (t2 - tStart) + " ms");

        final int[][] neighbors = new int[vCount][];
        for (int i = 0; i < vCount; i++)
            neighbors[i] = new int[d[i]];

        int[] pos = new int[vCount];
        int[] flen = new int[vCount];
        for (Edge e : edges) {
            int dv1 = d[e.v1];
            int dv2 = d[e.v2];
            if (dv1 == dv2) {
                dv1 = e.v1;
                dv2 = e.v2;
            }
            if (dv1 < dv2) {
                neighbors[e.v1][flen[e.v1]++] = e.v2;
                neighbors[e.v2][d[e.v2] - 1 - pos[e.v2]++] = e.v1;

            } else {
                neighbors[e.v2][flen[e.v2]++] = e.v1;
                neighbors[e.v1][d[e.v1] - 1 - pos[e.v1]++] = e.v2;
            }
        }

        long tInitFonl = System.currentTimeMillis();
        System.out.println("initialize fonl " + (tInitFonl - t2) + " ms");

        final VertexCompare vertexCompare = new VertexCompare(d);
        batchSelector = new AtomicInteger(0);
        forkJoinPool.submit(() -> IntStream.range(0, threads).parallel().forEach(i -> {
            int maxFonlSize = 0;
            while (true) {
                int start = batchSelector.getAndAdd(BATCH_SIZE);
                if (start >= vCount)
                    break;
                int end = Math.min(vCount, BATCH_SIZE + start);

                for (int u = start; u < end; u++) {
                    if (flen[u] < 2)
                        continue;
                    vertexCompare.quickSort(neighbors[u], 0, flen[u] - 1);
                    if (maxFonlSize < flen[u])
                        maxFonlSize = flen[u];
                    Arrays.sort(neighbors[u], flen[u], neighbors[u].length);
                }
            }
        })).get();

        long tSort = System.currentTimeMillis();
        System.out.println("sort fonl in " + (tSort - t2) + " ms");

        Long2ObjectOpenHashMap<IntSet>[] mapThreads = new Long2ObjectOpenHashMap[threads];
        for (int i = 0; i < threads; i++) {
            mapThreads[i] = new Long2ObjectOpenHashMap(vCount);
        }

        batchSelector = new AtomicInteger(0);
        forkJoinPool.submit(() -> IntStream.range(0, threads).parallel().forEach(thread -> {
            while (true) {
                int start = batchSelector.getAndAdd(BATCH_SIZE);
                if (start >= vCount)
                    break;
                int end = Math.min(vCount, BATCH_SIZE + start);

                for (int u = start; u < end; u++) {
                    if (flen[u] < 2)
                        continue;

                    int[] neighborsU = neighbors[u];
                    // Find triangle by checking connectivity of neighbors
                    for (int vIndex = 0; vIndex < flen[u]; vIndex++) {
                        int v = neighborsU[vIndex];
                        int[] vNeighbors = neighbors[v];

                        long uv = (long) u << 32 | v & 0xFFFFFFFFL;

                        int count = 0;
                        // intersection on u neighbors and v neighbors
                        int uwIndex = vIndex + 1, vwIndex = 0;

                        while (uwIndex < flen[u] && vwIndex < flen[v]) {
                            if (neighborsU[uwIndex] == vNeighbors[vwIndex]) {
                                int w = neighborsU[uwIndex];
                                IntSet set = mapThreads[thread].get(uv);
                                if (set == null) {
                                    set = new IntOpenHashSet();
                                    mapThreads[thread].put(uv, set);
                                }
                                set.add(w);

                                long uw = (long) u << 32 | w & 0xFFFFFFFFL;
                                set = mapThreads[thread].get(uw);
                                if (set == null) {
                                    set = new IntOpenHashSet();
                                    mapThreads[thread].put(uw, set);
                                }
                                set.add(v);

                                long vw = (long) v << 32 | w & 0xFFFFFFFFL;
                                set = mapThreads[thread].get(vw);
                                if (set == null) {
                                    set = new IntOpenHashSet();
                                    mapThreads[thread].put(vw, set);
                                }
                                set.add(u);

                                uwIndex++;
                                vwIndex++;
                            } else if (vertexCompare.compare(neighborsU[uwIndex], vNeighbors[vwIndex]) == -1)
                                uwIndex++;
                            else
                                vwIndex++;
                        }

                    }
                }
            }
        })).get();

        long tTC = System.currentTimeMillis();
        System.out.println("tc duration: " + (tTC - tSort) + " ms");

        int max = 0;
        for (int i = 0 ; i < threads; i ++) {
            if (max < mapThreads[i].size())
                max = mapThreads[i].size();
        }

        Long2ObjectMap<IntSet> map = Long2ObjectMaps.synchronize(mapThreads[0]);
//        Long2ObjectOpenHashMap<IntSet> map = new Long2ObjectOpenHashMap<>(max * 2);
        map.putAll(mapThreads[0]);
        for (int i = 1 ; i < threads; i ++) {
            final int index = i;
            forkJoinPool.submit(() -> mapThreads[index].long2ObjectEntrySet().parallelStream().forEach(edge -> {
                IntSet set = map.get(edge.getLongKey());
                if (set == null)
                    map.put(edge.getLongKey(), edge.getValue());
                else
                    set.addAll(edge.getValue());
            })).get();
//            ObjectIterator<Long2ObjectMap.Entry<IntSet>> iter = mapThreads[i].long2ObjectEntrySet().fastIterator();
//            while (iter.hasNext()) {
//                Long2ObjectMap.Entry<IntSet> entry = iter.next();
//                IntSet set = map.get(entry.getLongKey());
//                if (set == null)
//                    map.put(entry.getLongKey(), entry.getValue());
//                else
//                    set.addAll(entry.getValue());
//            }
        }
//        forkJoinPool.submit(() -> IntStream.range(1, threads).parallel().forEach(thread -> {
//            map.putAll(mapThreads[thread]);
//        })).get();
        long tAgg = System.currentTimeMillis();
        System.out.println("Aggregate in " + (tAgg - tTC) + " ms");
    }
}
