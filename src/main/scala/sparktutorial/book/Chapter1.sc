import org.apache.spark.{SparkConf, SparkContext}

val sc = new SparkContext("spark://10.2.64.20:4040", "book", new SparkConf())

//val rdd2 = sc.textFile("/Users/bioqwer/Development/spark-scala-tutorial/data/linkage")

val rdd2 = sc.textFile("///user/afalexandrov/linkage")


def isHeader(line: String): Boolean = {
  line.contains("id_1")
}

case class MatchData(id1: Int, id2: Int, scores: Array[Double], matched: Boolean) {
  override def toString: String = s"id1=$id1 id2=$id2 scores= ${scores.deep} matched=$matched"
}

def toDouble(s: String) = {
  if ("?".equals(s)) Double.NaN else s.toDouble
}

//Теперь можно модифицировать метод parse так, чтобы он возвращал экземпляр case-класса MatchData вместо кортежа:
def parse(line: String): MatchData = {
  val pieces = line.split(',')
  val id1 = pieces(0).toInt
  val id2 = pieces(1).toInt
  val scores = pieces.slice(2, 11).map(toDouble)
  val matched = pieces(11).toBoolean
  MatchData(id1, id2, scores, matched)
}

val data = rdd2.filter(x => !isHeader(x))
data.cache()
val parsed = data.map(parse)
parsed.take(10)

val grouped = parsed.groupBy(_.matched)
grouped.mapValues(_.size).foreach(println)

val matchCounts = parsed.map(md => md.matched).countByValue()
val matchCountsSeq = matchCounts.toSeq

import java.lang.Double.isNaN

parsed.map(md => md.scores(0)).filter(!isNaN(_)).stats()

val stats = (0 until 9).map(i => {
  parsed.map(md => md.scores(i)).filter(!isNaN(_)).stats()
})
stats(1)

class NAStatCounter extends Serializable {

  val stats: org.apache.spark.util.StatCounter = org.apache.spark.util.StatCounter()
  var missing: Long = 0

  def add(x: Double): NAStatCounter = {
    if (java.lang.Double.isNaN(x)) {
      missing += 1
    } else {
      stats.merge(x)
    }
    this
  }

  def merge(other: NAStatCounter): NAStatCounter = {
    stats.merge(other.stats)
    missing += other.missing
    this
  }

  override def toString = {
    "stats: " + stats.toString + " NaN: " + missing
  }
}

object NAStatCounter extends Serializable {
  def apply(x: Double) = new NAStatCounter().add(x)
}

val nasRDD = parsed.map(md => {
  md.scores.map(d => NAStatCounter(d))
})

val nas1 = NAStatCounter(10.0)
nas1.add(2.1)
val nas2 = NAStatCounter(Double.NaN)
nas1.merge(nas2)

val arr = Array(1.0, Double.NaN, 17.29)
val nas = arr.map(d => NAStatCounter(d))