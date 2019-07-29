package com.test.kudu

import org.slf4j.LoggerFactory
import org.apache.spark.SparkConf
import org.apache.log4j.Logger
import org.apache.log4j.Level
import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.kafka.KafkaUtils
import kafka.serializer.StringDecoder
import org.apache.spark.streaming.Minutes
import org.apache.spark.streaming.Seconds
import org.apache.kudu.spark.kudu.KuduContext

case class Cust(id: Long, name: String)
case class CommonStreamingParams(zkQuorum: String, groupId: String, brokers: String, topic: String, window: Int)
object SaveStreaming2Kudu {
  def main(args: Array[String]): Unit = {
    Logger.getLogger("org.apache.spark").setLevel(Level.ERROR)
    val conf = new SparkConf().setAppName("SaveStreaming2Kudu") //.setMaster("local[*]")
      .set("spark.sql.warehouse.dir", "/user/hive/warehouse/spark-warehouse")
      .set("spark.hadoop.fs.defaultFS", "hdfs://master9102:8020")
    val ssc = new StreamingContext(conf, Seconds(5))
    val topics = Set("testkudu")
    val kafkaPrams = Map[String, String]("metadata.broker.list" -> "172.16.9.109:9092,172.16.9.110:9092")
    val dStreams = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaPrams, topics)

    val sqlContext = new SQLContext(ssc.sparkContext)
    import sqlContext.implicits._

    val kuduContext = new KuduContext("slave9114:7051", ssc.sparkContext)
    //    kuduContext.insertRows(custDF, "impala::default.test_kudu_table")
    //这里的处理方式是将dStreams foreachRdd  在foreach是封装成RDD,之后写入kudu
    dStreams.foreachRDD(rdd => {
      val newRdd = rdd.map(x => {
        val s = x._2.split("\\,")
        Cust(s(0).toLong, s(1))
      })
      val custDF = sqlContext.createDataFrame(newRdd)
      println(custDF.count())
      kuduContext.upsertRows(custDF, "impala::default.test_kudu_table")
    })

    ssc.start()
    ssc.awaitTermination()
  }

}