package ir.ac.sbu.graph.spark.pattern;

import com.typesafe.config.Config;
import ir.ac.sbu.graph.spark.SparkAppConf;
import ir.ac.sbu.graph.spark.pattern.index.IndexRow;
import ir.ac.sbu.graph.spark.pattern.index.fonl.value.*;
import ir.ac.sbu.graph.spark.pattern.query.Subquery;
import ir.ac.sbu.graph.spark.pattern.search.MatchCount;
import ir.ac.sbu.graph.spark.pattern.search.PatternCounter;
import ir.ac.sbu.graph.types.Edge;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.apache.hadoop.conf.Configuration;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SQLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;

import java.util.List;

public class PatternConfig {
    private static final Logger logger = LoggerFactory.getLogger(PatternConfig.class);

    private final String inputDir;
    private final String targetGraph;

    private final boolean useDefaultLabel;
    private final String labelPath;
    private final List<String> labels;

    private final String querySample;
    private final String indexDir;

    private final String sparkMaster;
    private final int partitionNum;
    private final int cores;
    private final int driverMemoryGB;
    private String app;

    private final String hdfsMaster;
    private final SparkAppConf sparkAppConf;
    private final Configuration hadoopConf;

    public PatternConfig(Config conf, String app) {
        this.app = app;
        inputDir = conf.getString("inputDir");
        targetGraph = conf.getString("targetGraph");
        labelPath = inputDir + "/" + targetGraph + ".lbl";
        useDefaultLabel = conf.getBoolean("defaultLabel");
        labels = conf.getStringList("labels");

        querySample = conf.getString("querySample");

        indexDir = conf.getString("indexDir");

        sparkMaster = conf.getString("sparkMaster");
        partitionNum = conf.getInt("partitionNum");
        cores = conf.getInt("cores");
        driverMemoryGB = conf.getInt("driverMemoryGB");
        sparkAppConf = createSparkAppConf();
        sparkAppConf.init();

        hdfsMaster = conf.getString("hdfsMaster");
        hadoopConf = new Configuration();
        hadoopConf.set("fs.defaultFS", hdfsMaster);
        hadoopConf.set("io.serializations", "org.apache.hadoop.io.serializer.WritableSerialization");

        logger.info("(SBM) ****************** Config Properties ******************");
        logger.info("(SBM) " + toString());
        logger.info("(SBM) *******************************************************");
    }

    private SparkAppConf createSparkAppConf() {

        return new SparkAppConf() {

            @Override
            public void init() {
                partitionNum = PatternConfig.this.partitionNum;
                graphInputPath = PatternConfig.this.inputDir + PatternConfig.this.targetGraph;

                String query = app.contains("search") ? PatternConfig.this.querySample + "-": "";
                int cores = PatternConfig.this.cores;
                sparkConf = new SparkConf()
                        .setAppName("pattern-" + app + "-p" + partitionNum + "-c" + cores + "-" +
                                query + "[" + PatternConfig.this.targetGraph + "]")
                        .setMaster(PatternConfig.this.sparkMaster)
                        .set("spark.cores.max", cores + "")
                        .set("spark.driver.memory", PatternConfig.this.driverMemoryGB + "g")
                        .set("spark.driver.maxResultSize", PatternConfig.this.driverMemoryGB + "g")
                        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                        .registerKryoClasses(new Class[]{
                                IndexRow.class,
                                FonlValue.class,
                                MatchCount.class,
                                PatternCounter.class,
                                Subquery.class,
                                TriangleFonlValue.class,
                                Meta.class,
                                LabelDegreeTriangleMeta.class,
                                TriangleMeta.class,
                                Edge.class,
                                List.class,
                                Iterable.class,
                                long[].class,
                                int[].class,
                                String[].class,
                                Subquery.class,
                                Tuple2[].class,
                                Int2ObjectMap.class,
                                IntSet.class,
                                Int2ObjectOpenHashMap.class,
                                IntOpenHashSet.class
                        });

                javaSparkContext = new JavaSparkContext(sparkConf);
                sqlContext = new SQLContext(javaSparkContext);
            }
        };
    }

    public Configuration getHadoopConf() {
        return hadoopConf;
    }

    public SparkAppConf getSparkAppConf() {
        return sparkAppConf;
    }

    public JavaSparkContext getSparkContext() {
        return sparkAppConf.getJavaSparkContext();
    }

    public String getInputDir() {
        return inputDir;
    }

    public String getTargetGraph() {
        return targetGraph;
    }

    public String getLabelPath() {
        return labelPath;
    }

    public String getQuerySample() {
        return querySample;
    }

    public String getIndexPath() {
        return indexDir + "/" + targetGraph + ".idx";
    }

    public String getIndexDir() {
        return indexDir;
    }

    public String getSparkMaster() {
        return sparkMaster;
    }

    public int getPartitionNum() {
        return partitionNum;
    }

    public int getCores() {
        return cores;
    }

    public int getDriverMemoryGB() {
        return driverMemoryGB;
    }

    @Override
    public String toString() {

        return  "inputDir: " + inputDir + ", " +
                "targetGraph: " + targetGraph + ", " +
                "labelPath: " + labelPath + ", " +
                "querySample: " + querySample + ", " +
                "indexDir: " + indexDir + ", " +
                "sparkMaster: " + sparkMaster + ", " +
                "partitionNum: " + partitionNum + ", " +
                "cores: " + cores + ", " +
                "driverMemoryGB: " + driverMemoryGB + ", " +
                "hdfsMaster: " + hdfsMaster;
    }

    public List<String> getLabels() {
        return labels;
    }

    public boolean isUseDefaultLabel() {
        return useDefaultLabel;
    }
}
