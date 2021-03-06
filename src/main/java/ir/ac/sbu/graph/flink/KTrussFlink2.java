//package ir.ac.sbu.graph.flink;
//
//
//import it.unimi.dsi.fastutil.ints.IntArrayList;
//import it.unimi.dsi.fastutil.ints.IntList;
//import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
//import it.unimi.dsi.fastutil.ints.IntSet;
//import org.apache.flink.api.common.functions.CoGroupFunction;
//import org.apache.flink.api.common.functions.FlatJoinFunction;
//import org.apache.flink.api.common.functions.FlatMapFunction;
//import org.apache.flink.api.common.functions.GroupReduceFunction;
//import org.apache.flink.api.common.typeinfo.TypeHint;
//import org.apache.flink.api.java.DataSet;
//import org.apache.flink.api.java.ExecutionEnvironment;
//import org.apache.flink.api.java.operators.*;
//import org.apache.flink.api.java.tuple.Tuple2;
//import org.apache.flink.api.java.tuple.Tuple3;
//import org.apache.flink.util.Collector;
//
//import java.util.*;
//
//public class KTrussFlink2 {
//
//    public static final TypeHint<Tuple2<Integer, Integer>> TUPLE_2_TYPE_HINT = new TypeHint<Tuple2<Integer, Integer>>() {
//    };
//
//    public static final TypeHint<Tuple3<Integer, Integer, Integer>> TUPLE_3_TYPE_HINT = new TypeHint<Tuple3<Integer, Integer, Integer>>() {
//    };
//
//    public static final TypeHint<Tuple2<Integer, int[]>> TUPLE_2_INT_ARRAY_TYPE_HINT = new TypeHint<Tuple2<Integer, int[]>>() {
//    };
//    public static final TypeHint<Tuple2<Tuple2<Integer, Integer>, int[]>> TYPE_TUPLE2_INT_ARRAY = new TypeHint<Tuple2<Tuple2<Integer, Integer>, int[]>>() {
//    };
//    public static final TypeHint<Tuple2<Tuple2<Integer, Integer>, Integer>> TYPE_TUPLE2_INT = new TypeHint<Tuple2<Tuple2<Integer, Integer>, Integer>>() {
//    };
//    public static final TypeHint<Tuple2<Tuple2<Integer, Integer>, Tuple2<int[], int[]>>> TYPE_TUPLE2_2INT_ARRAY = new TypeHint<Tuple2<Tuple2<Integer, Integer>, Tuple2<int[], int[]>>>() {
//    };
//
//    public static void main(String[] args) throws Exception {
//        final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
//        String graphInputPath = "/home/mehdi/graph-data/com-amazon.ungraph.txt";
//
//        if (args.length > 0)
//            graphInputPath = args[0];
//
//        int k = 4; // k-truss
//        if (args.length > 1)
//            k = Integer.parseInt(args[1]);
//        final int minSup = k - 2;
//
//        env.getConfig().enableForceKryo();
////        env.getConfig().enableObjectReuse();
//        env.getConfig().registerPojoType(int[].class);
//
//        long startTime = System.currentTimeMillis();
//        FlatMapOperator<Tuple2<Integer, Integer>, Tuple2<Integer, Integer>> edges = env.readCsvFile(graphInputPath)
//            .fieldDelimiter("\t").ignoreComments("#")
//            .types(Integer.class, Integer.class)
//            .flatMap((Tuple2<Integer, Integer> t, Collector<Tuple2<Integer, Integer>> collector) -> {
//                collector.collect(t);
//                collector.collect(t.swap());
//            }).returns(TUPLE_2_TYPE_HINT);
//
//        GroupReduceOperator<Tuple3<Integer, Integer, Integer>, Tuple2<Integer, int[]>> fonls =
//            edges.groupBy(0).reduceGroup(new GroupReduceFunction<Tuple2<Integer, Integer>, Tuple3<Integer, Integer, Integer>>() {
//                final IntSet set = new IntOpenHashSet();
//
//                @Override
//                public void reduce(Iterable<Tuple2<Integer, Integer>> values, Collector<Tuple3<Integer, Integer, Integer>> collector)
//                    throws Exception {
//                    set.clear();
//                    int vertex = -1;
//                    for (Tuple2<Integer, Integer> value : values) {
//                        vertex = value.f0;
//                        set.add(value.f1);
//                    }
//
//                    if (set.support() == 0)
//                        return;
//
//                    for (Integer i : set) {
//                        collector.collect(new Tuple3<>(i, vertex, set.support()));
//                    }
//                }
//            }).returns(TUPLE_3_TYPE_HINT)
//                .groupBy(0).reduceGroup(new GroupReduceFunction<Tuple3<Integer, Integer, Integer>, Tuple2<Integer, int[]>>() {
//                final List<Tuple3<Integer, Integer, Integer>> list = new ArrayList<>();
//                final List<Tuple3<Integer, Integer, Integer>> holds = new ArrayList<>();
//
//                @Override
//                public void reduce(Iterable<Tuple3<Integer, Integer, Integer>> values, Collector<Tuple2<Integer, int[]>> collector)
//                    throws Exception {
//
//                    list.clear();
//                    for (Tuple3<Integer, Integer, Integer> value : values) {
//                        list.add(value);
//                    }
//
//                    int vertex = list.get(0).f0;
//                    int degree = list.support();
//                    holds.clear();
//
//                    for (int i = 0; i < degree; i++) {
//                        Tuple3<Integer, Integer, Integer> tuple = list.get(i);
//                        if (tuple.f2 > degree || (tuple.f2 == degree && tuple.f1 > tuple.f0))
//                            holds.add(tuple);
//                    }
//
//                    Collections.sort(holds, (a, sign) -> a.f2 != sign.f2 ? a.f2 - sign.f2 : a.f1 - sign.f1);
//
//                    int[] higherDegs = new int[holds.support() + 1];
//                    higherDegs[0] = degree;
//                    for (int i = 1; i < higherDegs.length; i++)
//                        higherDegs[i] = holds.get(i - 1).f1;
//
//                    collector.collect(new Tuple2<>(vertex, higherDegs));
//                }
//            }).returns(TUPLE_2_INT_ARRAY_TYPE_HINT);
//
//        FlatMapOperator<Tuple2<Integer, int[]>, Tuple2<Integer, int[]>> candidates = fonls.filter(t -> t.f1.length > 2)
//            .flatMap(new FlatMapFunction<Tuple2<Integer, int[]>, Tuple2<Integer, int[]>>() {
//                @Override
//                public void flatMap(Tuple2<Integer, int[]> t, Collector<Tuple2<Integer, int[]>> collector) throws Exception {
//                    int support = t.f1.length - 1;
//                    if (support == 1)
//                        return;
//                    for (int index = 1; index < support; index++) {
//                        int len = support - index;
//                        int[] cvalue = new int[len + 1];
//                        cvalue[0] = t.f0; // First vertex in the triangle
//                        System.arraycopy(t.f1, index + 1, cvalue, 1, len);
//                        Arrays.sort(cvalue, 1, cvalue.length); // quickSort to comfort with fonl
//                        collector.collect(new Tuple2<>(t.f1[index], cvalue));
//                    }
//                }
//            });
//
//        GroupReduceOperator<Tuple3<Integer, Integer, Integer>, Tuple2<Tuple2<Integer, Integer>, int[]>> edgeVertices = fonls.map(t -> {
//            Arrays.sort(t.f1, 1, t.f1.length);
//            return t;
//        }).returns(TUPLE_2_INT_ARRAY_TYPE_HINT).complement(candidates)
//            .where(0)
//            .equalTo(0)
//            .with((FlatJoinFunction<Tuple2<Integer, int[]>, Tuple2<Integer, int[]>, Tuple3<Integer, Integer, Integer>>)
//                (first, second, collector) -> {
//                    int[] fonl = first.f1;
//                    int[] can = second.f1;
//                    int vertex = first.f0;
//                    int u = can[0];
//                    Tuple2<Integer, Integer> uv;
//                    if (u < vertex)
//                        uv = new Tuple2<>(u, vertex);
//                    else
//                        uv = new Tuple2<>(vertex, u);
//
//                    // The intersection determines triangles which u and vertex are two of their vertices.
//                    // Always generate and edge (u, vertex) such that u < vertex.
//                    int fi = 1;
//                    int ci = 1;
//                    while (fi < fonl.length && ci < can.length) {
//                        if (fonl[fi] < can[ci])
//                            fi++;
//                        else if (fonl[fi] > can[ci])
//                            ci++;
//                        else {
//                            int w = fonl[fi];
//                            collector.collect(new Tuple3<>(uv.f0, uv.f1, w));
//                            if (u < w)
//                                collector.collect(new Tuple3<>(u, w, vertex));
//                            else
//                                collector.collect(new Tuple3<>(w, u, vertex));
//
//                            if (vertex < w)
//                                collector.collect(new Tuple3<>(vertex, w, u));
//                            else
//                                collector.collect(new Tuple3<>(w, vertex, u));
//                            fi++;
//                            ci++;
//                        }
//                    }
//                }).returns(TUPLE_3_TYPE_HINT).groupBy(0, 1).reduceGroup((Iterable<Tuple3<Integer, Integer, Integer>> values,
//                                                                         Collector<Tuple2<Tuple2<Integer, Integer>, int[]>> collector) -> {
//                IntList list = new IntArrayList();
//
//                Tuple2<Integer, Integer> key = null;
//                for (Tuple3<Integer, Integer, Integer> i : values) {
//                    if (key == null) {
//                        key = new Tuple2<>(i.f0, i.f1);
//                    }
//                    list.add(i.f2.intValue());
//                }
//                collector.collect(new Tuple2<>(key, list.toIntArray()));
//            }).returns(TYPE_TUPLE2_INT_ARRAY);
//
//        FilterOperator<Tuple2<Tuple2<Integer, Integer>, int[]>> initialWorkingSet = edgeVertices.filter(t -> t.f1.length < minSup);
//
//        List<Tuple2<Tuple2<Integer, Integer>, int[]>> emptyList = new ArrayList<>();
//        emptyList.add(new Tuple2<>(new Tuple2<>(-1, -1), new int[]{}));
//
//        DataSource<Tuple2<Tuple2<Integer, Integer>, int[]>> empty = env.fromCollection(emptyList);
//
//        DeltaIteration<Tuple2<Tuple2<Integer, Integer>, int[]>, Tuple2<Tuple2<Integer, Integer>, int[]>> iteration =
//            empty.iterateDelta(initialWorkingSet, 100, 0);
//
//        GroupReduceOperator<Tuple2<Tuple2<Integer, Integer>, Integer>, Tuple2<Tuple2<Integer, Integer>, int[]>> invUpdates =
//            iteration.getWorkset().filter(t -> t.f1.length < minSup)
//                .flatMap((Tuple2<Tuple2<Integer, Integer>, int[]> kv,
//                          Collector<Tuple2<Tuple2<Integer, Integer>, Integer>> collector) -> {
//                    int u = kv.f0.f0;
//                    int vertex = kv.f0.f1;
//                    for (int i = 0; i < kv.f1.length; i++) {
//                        int w = kv.f1[i];
//                        if (w < u)
//                            collector.collect(new Tuple2<>(new Tuple2<>(w, u), vertex));
//                        else
//                            collector.collect(new Tuple2<>(new Tuple2<>(u, w), vertex));
//
//                        if (w < vertex)
//                            collector.collect(new Tuple2<>(new Tuple2<>(w, vertex), u));
//                        else
//                            collector.collect(new Tuple2<>(new Tuple2<>(vertex, w), u));
//                    }
//                }).returns(TYPE_TUPLE2_INT)
//                .groupBy(0).reduceGroup((Iterable<Tuple2<Tuple2<Integer, Integer>, Integer>> values,
//                                         Collector<Tuple2<Tuple2<Integer, Integer>, int[]>> collector) -> {
//                IntList list = new IntArrayList();
//                Tuple2<Integer, Integer> key = null;
//                for (Tuple2<Tuple2<Integer, Integer>, Integer> value : values) {
//                    if (key == null) key = value.f0;
//                    list.add(value.f1.intValue());
//                }
//
//                if (key == null)
//                    return;
//
//                collector.collect(new Tuple2<>(key, list.toIntArray()));
//            }).returns(TYPE_TUPLE2_INT_ARRAY);
//
//        JoinOperator<Tuple2<Tuple2<Integer, Integer>, int[]>, Tuple2<Tuple2<Integer, Integer>, int[]>,
//            Tuple2<Tuple2<Integer, Integer>, Tuple2<int[], int[]>>> updateList =
//            edgeVertices.joinWithTiny(invUpdates)
//                .where(0)
//                .equalTo(0)
//                .with((Tuple2<Tuple2<Integer, Integer>, int[]> first,
//                       Tuple2<Tuple2<Integer, Integer>, int[]> second,
//                       Collector<Tuple2<Tuple2<Integer, Integer>, Tuple2<int[], int[]>>> collector) -> {
//
//                    collector.collect(new Tuple2<>(first.f0, new Tuple2<>(first.f1, second.f1)));
//                }).returns(TYPE_TUPLE2_2INT_ARRAY);
//
//        CoGroupOperator<Tuple2<Tuple2<Integer, Integer>, int[]>, Tuple2<Tuple2<Integer, Integer>, Tuple2<int[], int[]>>,
//            Tuple2<Tuple2<Integer, Integer>, int[]>> newWorkingSet =
//            iteration.getSolutionSet()
//                .coGroup(updateList)
//                .where(0)
//                .equalTo(0)
//                .with((Iterable<Tuple2<Tuple2<Integer, Integer>, int[]>> solution,
//                       Iterable<Tuple2<Tuple2<Integer, Integer>, Tuple2<int[], int[]>>> newWorkSet,
//                       Collector<Tuple2<Tuple2<Integer, Integer>, int[]>> collector) -> {
//                    Iterator<Tuple2<Tuple2<Integer, Integer>, Tuple2<int[], int[]>>> itNewWorkSet = newWorkSet.iterator();
//                    if (!itNewWorkSet.hasNext())
//                        return;
//                    Tuple2<Tuple2<Integer, Integer>, Tuple2<int[], int[]>> s = itNewWorkSet.next();
//
//                    IntSet set = new IntOpenHashSet(s.f1.f0);
//                    for (Tuple2<Tuple2<Integer, Integer>, int[]> invs : solution) {
//                        for (int i : invs.f1) {
//                            set.remove(i);
//                        }
//                    }
//
//                    for (int i : s.f1.f1) {
//                        set.remove(i);
//                    }
//
//                    if (set.support() == 0)
//                        return;
//
//                    if (set.support() >= minSup)
//                        return;
//
//                    collector.collect(new Tuple2<>(s.f0, set.toIntArray()));
//                }).returns(TYPE_TUPLE2_INT_ARRAY);
//
//        DataSet<Tuple2<Tuple2<Integer, Integer>, int[]>> result = iteration.closeWith(invUpdates, newWorkingSet);
//
//        CoGroupOperator<Tuple2<Tuple2<Integer, Integer>, int[]>, Tuple2<Tuple2<Integer, Integer>, int[]>,
//            Tuple2<Integer, Integer>> remained =
//            edgeVertices.coGroup(result).where(0).equalTo(0).with((Iterable<Tuple2<Tuple2<Integer, Integer>, int[]>> first,
//                                                                   Iterable<Tuple2<Tuple2<Integer, Integer>, int[]>> second,
//                                                                   Collector<Tuple2<Integer, Integer>> collector) -> {
//
//                Iterator<Tuple2<Tuple2<Integer, Integer>, int[]>> iterator = first.iterator();
//                if (!iterator.hasNext())
//                    return;
//
//                Tuple2<Tuple2<Integer, Integer>, int[]> origin = iterator.next();
//                IntSet set = new IntOpenHashSet(origin.f1);
//                for (Tuple2<Tuple2<Integer, Integer>, int[]> t : second) {
//                    for (int i : t.f1) {
//                        set.remove(i);
//                    }
//                }
//                if (set.support() < minSup)
//                    return;
//
//                collector.collect(origin.f0);
//            }).returns(new TypeHint<Tuple2<Integer, Integer>>() {
//            });
//
//        long count = remained.count();
//        long endTime = System.currentTimeMillis();
//        System.out.println("result count: " + count + ", duration: " + (endTime - startTime));
//    }
//}
