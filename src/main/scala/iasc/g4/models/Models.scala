package iasc.g4.models

import akka.actor.Cancellable
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.ServiceKey
import akka.http.scaladsl.model.DateTime
import iasc.g4.CborSerializable
import iasc.g4.actors.AuctionActor
import iasc.g4.models.Models.{Command, OperationPerformed}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}



/**
 * Modelos de la app y su transformación a JSON
 */
object Models {
  trait Command extends CborSerializable
  trait InternalCommand extends Command
  final case class OperationPerformed(description: String)
  final case class Buyer(name: String, ip: String, tags: Set[String])
  final case class Buyers(buyers: Set[Buyer])
  final case class Auction(id:String, basePrice: Double, duration: Long, tags: Set[String], article: String)
  final case class Auctions(auctions: Set[Auction])
  final case class Bid(auctionId:String, buyerName : String, price : Double)
  final case class AuctionInstance(index :Long, id:String,  isFree:Boolean)
  final case class AuctionActorState(auction : Auction , price : Double, currentWinner: String ,buyers : Set[String], endTime : DateTime)

  import DefaultJsonProtocol._
  implicit val buyerJsonFormat: RootJsonFormat[Buyer] = jsonFormat3(Buyer)
  implicit val buyersJsonFormat: RootJsonFormat[Buyers] = jsonFormat1(Buyers)
  implicit val auctionJsonFormat: RootJsonFormat[Auction] = jsonFormat5(Auction)
  implicit val auctionsJsonFormat: RootJsonFormat[Auctions] = jsonFormat1(Auctions)
  implicit val bidJsonFormat: RootJsonFormat[Bid] = jsonFormat3(Bid)
  implicit val auctionInstance: RootJsonFormat[AuctionInstance] = jsonFormat3(AuctionInstance)
}