package ir.ac.sbu.graph.spark;

import ir.ac.sbu.graph.utils.GraphUtils;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.storage.StorageLevel;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static ir.ac.sbu.graph.utils.Log.log;

/**
 * Calculate local clustering coefficient per vertex
 */
public class LocalCC extends SparkApp {

    private Triangle triangle;


    private JavaPairRDD<Integer, Float> lcc;

    public LocalCC(Triangle triangle) {
        super(triangle);
        this.triangle = triangle;
    }

    public JavaPairRDD<Integer, Float> getLcc() {
        if (lcc == null)
            generateLcc();

        return lcc;
    }

    public void generateLcc() {
        lcc = triangle.getFonl()
                .leftOuterJoin(triangle.getVertexTC())
                .mapValues(v -> (!v._2.isPresent() || v._1[0] < 2) ? 0.0f : 2.0f * v._2.get() / (v._1[0] * (v._1[0] - 1)))
                .persist(StorageLevel.MEMORY_AND_DISK());
    }

    public float avgLCC() {
        Float sum = getLcc().map(kv -> kv._2).reduce((a, b) -> a + b);
        long count = getLcc().count();
        return sum / count;
    }

    public static void main(String[] args) {

        long t1 = System.currentTimeMillis();
        SparkAppConf conf = new SparkAppConf(new ArgumentReader(args));
        conf.init();

        LocalCC lcc = new LocalCC(new Triangle(new NeighborList(new EdgeLoader(conf))));
        lcc.generateLcc();

        long t2 = System.currentTimeMillis();

        log("Average lcc: " + lcc.avgLCC(), t1, t2);

        lcc.close();
    }
}