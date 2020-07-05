package iasc.g4.models

import spray.json.{DefaultJsonProtocol, RootJsonFormat}

/**
 * Modelos de la app y su transformación a JSON
 */
object Models {
  trait Command;
  final case class OperationPerformed(description: String)
  final case class Buyer(name: String, ip: String, tags: Set[String])
  final case class Buyers(buyers: Set[Buyer])
  final case class Auction(basePrice: Double, duration: Long, tags: Set[String], article: String)
  final case class Auctions(auctions: Set[Auction])

  import DefaultJsonProtocol._
  implicit val buyerJsonFormat: RootJsonFormat[Buyer] = jsonFormat3(Buyer)
  implicit val buyersJsonFormat: RootJsonFormat[Buyers] = jsonFormat1(Buyers)
  implicit val auctionJsonFormat: RootJsonFormat[Auction] = jsonFormat4(Auction)
  implicit val auctionsJsonFormat: RootJsonFormat[Auctions] = jsonFormat1(Auctions)
}
