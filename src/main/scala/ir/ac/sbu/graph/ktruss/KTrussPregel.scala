package ir.ac.sbu.graph.ktruss

import ir.ac.sbu.graph.GraphUtils
import org.apache.spark.graphx._
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable.{ListBuffer, Map}

/**
  * Find ktruss subgraph using pregel like procedure.
  * This implemented pregel like system
  */
object KTrussPregel {

    case class OneNeighborMsg(vId: Long, neighbors: Array[Long])

    case class NeighborMessage(list: ListBuffer[OneNeighborMsg])


    def main(args: Array[String]): Unit = {
        var inputPath = "/home/mehdi/ir.ac.sbu.graph-data/com-amazon.ungraph.txt"
        if (args != null && args.length > 0)
            inputPath = args(0);

        var partition = 2
        if (args != null && args.length > 1)
            partition = args(1).toInt;

        var k = 4
        if (args.length > 2)
            k = args(2).toInt
        val support: Int = k - 2

        val conf = new SparkConf()
        if (args == null || args.length == 0)
            conf.setMaster("local[2]")
        GraphUtils.setAppName(conf, "KTruss-Pregel-" + k, partition, inputPath);

        val sc = SparkContext.getOrCreate(conf)

        val start = System.currentTimeMillis()
        // Load int ir.ac.sbu.graph which is as a list of edges
        val inputGraph = GraphLoader.edgeListFile(sc, inputPath, numEdgePartitions = partition)

        // Change direction from lower degree node to a higher node
        // First find degree of each node
        // Second find correct edge direction
        // Third create a new ir.ac.sbu.graph with new edges and previous vertices

        // Set degree of each vertex in the property.
        val graphVD = inputGraph.outerJoinVertices(inputGraph.degrees)((vid, v, deg) => deg)

        // Find new edges with correct direction. A direction from a lower degree node to a higher degree node.
        val newEdges = graphVD.triplets.map { et =>
            if (et.srcAttr.get <= et.dstAttr.get)
                Edge(et.srcId, et.dstId, 0)
            else
                Edge(et.dstId, et.srcId, 0)
        }

        val empty = sc.makeRDD(Array[(Long, Integer)]())

        // Create ir.ac.sbu.graph with edge direction from lower degree to higher degree node and edge attribute.
        var graph = Graph(empty, newEdges)

        // In a loop we find triangles and then remove edges lower than specified support
        var stop = false
        var iteration  = 0
        while (!stop) {
            iteration = iteration + 1
            println("iteration: " + iteration)
            graph.persist()
            val oldEdgeCount = graph.edges.count()
            // =======================================================
            // phase 1: Send message about completing the third edges.
            // =======================================================

            // Find outlink neighbors ids
            val neighborIds = graph.collectNeighborIds(EdgeDirection.Either)

            // Update each nodes with its outlink neighbors' id.
            val graphWithOutlinks = graph.outerJoinVertices(neighborIds)((vid, _, nId) => nId.getOrElse(Array[Long]()))

            // Send neighborIds of a node to all other its neighbors.
            // Send neighborIds of a node to all other its neighbors.
            val message = graphWithOutlinks.aggregateMessages(
                (ctx: EdgeContext[Array[Long], Int, List[(Long, Array[Long])]]) => {
                    val msg = List((ctx.srcId, ctx.srcAttr))
                    ctx.sendToDst(msg)
                }, (msg1: List[(Long, Array[Long])], msg2: List[(Long, Array[Long])]) => msg1 ::: msg2)

            // =======================================================
            // phase 2: Find triangles
            // =======================================================
            // At first each node receives messages from its neighbor telling their neighbors' id.
            // Then check that if receiving neighborIds have a common with its neighbors.
            // If there was any common neighbors then it report back telling the sender the completing nodes to make
            // a triangle through it.
            val triangleMsg = graphWithOutlinks.vertices.join(message).flatMap{ case (vid, (n, msg)) =>
                msg.map(ids => (ids._1, vid -> n.intersect(ids._2).length)).filter(t => t._2._2 > 0)
            }.groupByKey()

            // In this step tgraph have information about the common neighbors per neighbor as the follow:
            // (neighborId, array of common neighbors with neighborId)
            val edgeCount = graphWithOutlinks.outerJoinVertices(triangleMsg)((vid, n, msg) => {
                val m = Map[Long, Int]()
                msg.getOrElse(Map[Long, Int]()).map(t => m.put(t._1, t._2 + m.getOrElse(t._1, 0)))
                m
            })

            val edgeUpdated = edgeCount.mapTriplets(t => t.srcAttr.getOrElse(t.dstId, 0)).subgraph(e => e.attr >=
              support, (vid, v) => true)
            graph = edgeUpdated.mapVertices((vId, v) => 0)
            // =======================================================
            // phase 3: Collate messages for each edge
            // =======================================================
            val newEdgeCount = graph.edges.count()

            println("KTRUSS New Edge Count: " + newEdgeCount)

            if (newEdgeCount == 0 || newEdgeCount == oldEdgeCount)
                stop = true
        }

        println("KTRUSS final ir.ac.sbu.graph edge count: " + graph.edges.count() + ", duration: " + (System.currentTimeMillis
        () - start) / 1000)
    }
}
