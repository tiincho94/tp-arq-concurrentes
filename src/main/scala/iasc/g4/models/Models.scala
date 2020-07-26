package iasc.g4.models

import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.ServiceKey
import iasc.g4.CborSerializable
import iasc.g4.actors.AuctionActor
import iasc.g4.models.Models.{Command, OperationPerformed}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

/**
 * Modelos de la app y su transformación a JSON
 */
object Models {
  trait Command extends CborSerializable;
  final case class OperationPerformed(description: String)
  final case class Buyer(name: String, ip: String, tags: Set[String])
  final case class Buyers(buyers: Set[Buyer])
  final case class Auction(id:String, basePrice: Double, duration: Long, tags: Set[String], article: String)
  final case class Auctions(auctions: Set[Auction])
  final case class Bid(auctionId:String, buyerName : String, price : Double)

  import DefaultJsonProtocol._
  implicit val buyerJsonFormat: RootJsonFormat[Buyer] = jsonFormat3(Buyer)
  implicit val buyersJsonFormat: RootJsonFormat[Buyers] = jsonFormat1(Buyers)
  implicit val auctionJsonFormat: RootJsonFormat[Auction] = jsonFormat5(Auction)
  implicit val auctionsJsonFormat: RootJsonFormat[Auctions] = jsonFormat1(Auctions)
  implicit val bidJsonFormat: RootJsonFormat[Bid] = jsonFormat3(Bid)
}

abstract class AuctionInstance() {
  var index :Long
  var id:String
  var isFree:Boolean
  var auctionActorServiceKey: ServiceKey[Command]

  def setIndex(_index:Long):Unit = {
    this.index = _index
  }
  def getIndex(): Long ={
    return index
  }
  def setId(_id:String):Unit = {
    this.id = _id
  }
  def getId(): String ={
    return id
  }
  def setAuctionKey(_auctionActorServiceKey: ServiceKey[Command]) = {
    this.auctionActorServiceKey = _auctionActorServiceKey
  }
  def getAuctionKey(): ServiceKey[Command] ={
    return auctionActorServiceKey
  }
  def setIsFree(_isFree : Boolean) = {
    this.isFree = _isFree
  }
  def getIsFree() : Boolean = {
    return isFree
  }
}