package ir.ac.sbu.graph.ktruss.parallel;

import ir.ac.sbu.graph.Edge;
import ir.ac.sbu.graph.GraphLoader;

import static ir.ac.sbu.graph.MultiCoreUtils.createBuckets;

/**
 * Truss Decomposition based on Edge TriangleParallel list.
 */
public class KTrussParallel {

    public static void main(String[] args) throws Exception {
        String inputPath = "/home/mehdi/graph-data/com-amazon.ungraph.txt";
//        String inputPath = "/home/mehdi/graph-data/cit-Patents.txt";
//        String inputPath = "/home/mehdi/graph-data/Email-EuAll.txt";
        if (args.length > 0)
            inputPath = args[0];

        int k = 4; // k-truss
        if (args.length > 1)
            k = Integer.parseInt(args[1]);
        int minSup = k - 2;

        int threads = 4;
        if (args.length > 2)
            threads = Integer.parseInt(args[2]);

        int method = 4;
        if (args.length > 3)
            method = Integer.parseInt(args[3]);
        Edge[] edges = GraphLoader.loadFromLocalFile(inputPath);

        long t1 = System.currentTimeMillis();
        ParallelKTrussBase parallelKTruss = null;
        switch (method) {
            case 1: parallelKTruss = new ParallelKTruss1(edges, minSup, threads);
                break;
            case 2: parallelKTruss = new ParallelKTruss2(edges, minSup, threads);
                break;
            case 3: parallelKTruss = new ParallelKTruss3(edges, minSup, threads);
                break;
            case 4: parallelKTruss = new ParallelKTruss4(edges, minSup, threads);
                break;
            case 5: parallelKTruss = new ParallelKTruss5(edges, minSup, threads);
        }

        System.out.println("start ktruss with k = " + k + ", threads = " + threads + ", " +
            "method = " + method + " input: " + inputPath);

        parallelKTruss.start();
        long t2 = System.currentTimeMillis();

        System.out.println("duration: " + (t2- t1) + " ms");
    }
}
