package ir.ac.sbu.graph.ktruss.spark;

import ir.ac.sbu.graph.utils.GraphUtils;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.apache.spark.api.java.JavaPairRDD;
import scala.Tuple2;

import java.util.*;

import static ir.ac.sbu.graph.utils.Log.log;

public class KTrussInvalidUpdates extends KTruss {

    public static final IntSet EMPTY_SET = new IntOpenHashSet();
    public static final Integer INV_VERTEX = Integer.MIN_VALUE;

    public KTrussInvalidUpdates(KTrussConf conf) {
        super(conf);
    }

    public long start(JavaPairRDD<Integer, Integer> edges) {

        JavaPairRDD<Tuple2<Integer, Integer>, IntSet> tVertices = triangleVertices(edges);
        final int minSup = conf.k - 2;

        JavaPairRDD<Tuple2<Integer, Integer>, IntSet> currentInvalids = tVertices.filter(t -> t._2.size() < minSup)
            .partitionBy(partitioner2).cache();

        JavaPairRDD<Tuple2<Integer, Integer>, Tuple2<Boolean, IntSet>> invUpdates =
            sc.parallelize(new ArrayList<Tuple2<Tuple2<Integer, Integer>, Tuple2<Boolean, IntSet>>>()).mapToPair(t -> new Tuple2<>(t._1, t._2));

        int iteration = 0;
        while (true) {
            long invCount = currentInvalids.count();
            if (invCount == 0)
                break;

            log("iteration: " + ++iteration + ", currentInvalids: " + invCount);

            JavaPairRDD<Tuple2<Integer, Integer>, Integer> currentInvUpdate = currentInvalids.flatMapToPair(t -> {
                List<Tuple2<Tuple2<Integer, Integer>, Integer>> list = new ArrayList<>();
                list.add(new Tuple2<>(t._1, INV_VERTEX));
                int u = t._1._1;
                int v = t._1._2;

                IntIterator iter = t._2.iterator();
                while (iter.hasNext()) {
                    int w = iter.nextInt();
                    if (u < w)
                        list.add(new Tuple2<>(new Tuple2<>(u, w), v));
                    else
                        list.add(new Tuple2<>(new Tuple2<>(w, u), v));

                    if (v < w)
                        list.add(new Tuple2<>(new Tuple2<>(v, w), u));
                    else
                        list.add(new Tuple2<>(new Tuple2<>(w, v), u));
                }
                return list.iterator();
            }).partitionBy(partitioner2).cache();

            invUpdates = invUpdates.cogroup(currentInvUpdate).mapValues(t -> {
                Iterator<Tuple2<Boolean, IntSet>> preUpdates = t._1.iterator();
                Tuple2<Boolean, IntSet> value;
                IntSet set;
                if (preUpdates.hasNext()) {
                    value = preUpdates.next();
                    if (value._1)
                        return value;
                    set = value._2;
                } else {
                    set = new IntOpenHashSet();
                    value = new Tuple2<>(false, set);
                }

                for (Integer v : t._2) {
                    if (INV_VERTEX.equals(v))
                        return new Tuple2<>(true, EMPTY_SET);
                    set.add(v.intValue());
                }

                return value;
            }).partitionBy(partitioner2).cache();

            currentInvalids = tVertices.join(invUpdates).flatMapToPair(t -> {
                Tuple2<Boolean, IntSet> allInvalids = t._2._2;
                IntSet tSet = t._2._1;

                if (allInvalids._1)
                    return Collections.emptyIterator();

                tSet.removeAll(allInvalids._2);

                if (tSet.size() < minSup) {
                    return Arrays.asList(new Tuple2<>(t._1, tSet)).iterator();
                }

                return Collections.emptyIterator();
            }).partitionBy(partitioner2).cache();
        }

        return tVertices.subtractByKey(invUpdates.filter(t -> t._2._1)).count();
    }

    public static void main(String[] args) {
        KTrussConf conf = new KTrussConf(args, KTrussSparkTriangleVertices.class.getSimpleName(),
            GraphUtils.VertexDegree.class, long[].class, List.class);
        KTrussInvalidUpdates kTruss = new KTrussInvalidUpdates(conf);

        long tload = System.currentTimeMillis();
        JavaPairRDD<Integer, Integer> edges = kTruss.loadEdges();
        log("Load edges ", tload, System.currentTimeMillis());

        long start = System.currentTimeMillis();
        long edgeCount = kTruss.start(edges);
        long duration = System.currentTimeMillis() - start;

        kTruss.close();

        log("KTruss Edge Count: " + edgeCount, duration);
    }
}