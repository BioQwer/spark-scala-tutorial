import org.apache.spark.sql.SparkSession

val sc = SparkSession
  .builder()
  .enableHiveSupport()
  .appName("")
  .getOrCreate().sparkContext

val pathToFiles = "hdfs:///user/afalexandrov/study/music/"
val rawUserArtistData = sc.textFile(pathToFiles + "user_artist_data.txt")
rawUserArtistData.cache()

rawUserArtistData.map(_.split(' ')(0).toDouble).stats()
rawUserArtistData.map(_.split(' ')(1).toDouble).stats()

val rawArtistData = sc.textFile(pathToFiles + "artist_data.txt")
rawArtistData.cache()

val artistByIDFail = rawArtistData.map { line =>
  val (id, name) = line.span(_ != '\t')
  (id.toInt, name.trim)
}

val artistByID = rawArtistData.flatMap({ line =>
  val (id, name) = line.span(_ != '\t')
  if (name.isEmpty) {
    None
  } else {
    try {
      Some((id.toInt, name.trim))
    } catch {
      case e: NumberFormatException => None
    }
  }
})

val rawArtistAlias = sc.textFile(pathToFiles + "artist_alias.txt")
val artistAlias = rawArtistAlias.flatMap { line =>
  val tokens = line.split('\t')
  if (tokens(0).isEmpty) {
    None
  } else {
    Some((tokens(0).toInt, tokens(1).toInt))
  }
}.collectAsMap()

import org.apache.spark.mllib.recommendation._
val bArtistAlias = sc.broadcast(artistAlias)
val trainData = rawUserArtistData.map { line =>
  val Array(userID, artistID, count) = line.split(' ').map(_.toInt)
  val finalArtistID =
    bArtistAlias.value.getOrElse(artistID, artistID)
  // Извлечь псевдоним исполнителя, если таковой существует,
  // в противном случае извлечь настоящее имя.
  Rating(userID, finalArtistID, count)
}.cache()

val model = ALS.trainImplicit(trainData, 10, 5, 0.01, 1.0)

model.userFeatures.mapValues(_.mkString(", ")).first()

val rawArtistsForUser = rawUserArtistData.map(_.split(' ')).
  filter { case Array(user,_,_) => user.toInt == 2093760 }
// Находим строки с пользователем 2093760
val existingProducts =
  rawArtistsForUser.map { case Array(_,artist,_) => artist.toInt }.
    collect().toSet
// Получаем неповторяющихся исполнителей
artistByID.filter { case (id, name) =>
  existingProducts.contains(id)
}.values.collect().foreach(println)

val recommendations = model.recommendProducts(2093760, 5)
recommendations.foreach(println)

val recommendedProductIDs = recommendations.map(_.product).toSet
artistByID.filter { case (id, name) =>
  recommendedProductIDs.contains(id)
}.values.collect().foreach(println)

