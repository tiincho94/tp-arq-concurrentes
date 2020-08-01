package iasc.g4.actors

import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, Behaviors}
import akka.cluster.ddata.ReplicatedData
import akka.cluster.ddata.Replicator.{UpdateFailure, UpdateResponse, UpdateSuccess, UpdateTimeout}
import iasc.g4.actors.AuctionActor._
import iasc.g4.actors.AuctionSpawnerActor.{CreateAuction, FreeAuction, InternalCreateAuctionResponse, InternalDeleteAuctionResponse, InternalMakeBidResponse, readMajority, writeMajority}
import iasc.g4.models.Models.{Auction, AuctionActorState, AuctionInstance, Bid, Buyer, Buyers, Command, InternalCommand, OperationPerformed}
//import iasc.g4.util.Util.{getActors, _}
import iasc.g4.util.Util._

import scala.concurrent.duration._
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.ActorContext
import akka.cluster.ddata.Replicator.{ReadMajority, WriteMajority}
import akka.cluster.ddata.{Flag, LWWMap, LWWMapKey, SelfUniqueAddress}
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.Update
import iasc.g4.actors.NotifierSpawnerActor._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import akka.cluster.ddata.typed.scaladsl.Replicator.{Get, GetSuccess, Update}




/**
 * Actor que maneja una subasta
 */
object AuctionActor {
  //Mensajes Externos
  final case class StartAuction(auctionId:String, newAuction: Auction, replyTo: ActorRef[String]) extends Command
  final case class MakeBid(newBid:Bid, replyTo: ActorRef[String]) extends Command
  final case class Init(index:Long , auctionSpawner : ActorRef[AuctionSpawnerActor.AuctionSpawnerCommand]) extends Command
  final case class EndAuction(auctionId : String) extends Command
  final case class DeleteAuction(replyTo:ActorRef[String]) extends Command

  //Mensajes Internos
  private case class InternalStartAuctionResponse[A <: ReplicatedData](rsp: UpdateResponse[A],newAuction:Auction,replyTo: ActorRef[String]) extends InternalCommand
  private case class InternalEndAuctionResponse[A <: ReplicatedData](rsp: UpdateResponse[A],auctionId:String) extends InternalCommand


  def apply(index: Long,auctionActorServiceKey : ServiceKey[Command]): Behavior[Command] =
    Behaviors.setup(ctx => {
      new AuctionActor(ctx, index, auctionActorServiceKey)
    }
  )
  /*def apply(): Behavior[Command] =
    Behaviors.setup(ctx => new AuctionActor(ctx))*/
}

private class AuctionActor(
                            context: ActorContext[Command] ,
                            index:Long,
                            auctionActorServiceKey : ServiceKey[Command]
                          ) extends AbstractBehavior[Command](context) {


  var id : String = ""
  var price : Double = 0.0
  var duration : Long = 0
  var tags = Set[String]()
  var article : String = ""
  var currentWinner: String = null
  var buyers = Set[String]()
  var auction: Auction = null
  var timeout: Cancellable = null

  var auctionActorState: AuctionActorState = null

  implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress
  val DataKey = LWWMapKey[String, AuctionActorState]("auctionStatePool")

  private val readMajority = ReadMajority(getTimeout(context).duration)
  private val writeMajority = WriteMajority(getTimeout(context).duration)



  //val AuctionActorServiceKey = ServiceKey[Command](s"AuctionActor$index")
  context.system.receptionist ! Receptionist.Register(auctionActorServiceKey, context.self)
  println("Registrado")

  def resetVariables() = {
    this.id = ""
    this.price = 0.0
    this.duration = 0L
    this.tags = Set[String]()
    this.article = ""
    this.currentWinner = null
    this.buyers = Set[String]()
    this.auction = null
    this.timeout = null
  }

  override def onMessage(msg: Command): Behavior[Command] =
    DistributedData.withReplicatorMessageAdapter[Command, LWWMap[String, AuctionActorState]] { replicator =>
      msg match {
        //Init Start Auction
        case StartAuction(auctionId, newAuction, replyTo) =>
          println("Start Auction...")
          replicator.askUpdate(
            askReplyTo => Update(DataKey, LWWMap.empty[String, AuctionActorState], writeMajority, askReplyTo) {
              auctionStatePool => startAuction(auctionStatePool, auctionId,newAuction)
            },
            InternalStartAuctionResponse.apply(_,newAuction,replyTo))
          Behaviors.same
        case InternalStartAuctionResponse(_: UpdateSuccess[_],newAuction,replyTo) => {
          replyTo ! "Auction index: " + this.index.toString()
          getOneActor(context, NotifierSpawnerActor.NotifierSpawnerServiceKey) ! NotifyNewAuction(newAuction)
          Behaviors.same
        }
        case InternalStartAuctionResponse(_: UpdateTimeout[_],newAuction,replyTo) => Behaviors.same
        case InternalStartAuctionResponse(e: UpdateFailure[_],newAuction,replyTo) => throw new IllegalStateException("Unexpected failure: " + e)
        //End Start Auction

        // Init MakeBid
        case MakeBid(newBid, replyTo) =>
          if (newBid.price > this.price) {
            updateWinner(newBid.buyerName)
            this.price = newBid.price
            selfNotifyNewPrice(this.price) //TODO
            replyTo ! s"El nuevo precio es: |${this.price}| y el ganador por el momento es |${newBid.buyerName}|"
          } else {
            replyTo ! s"El precio enviado |${newBid.price}| puede ser menor que el establecido |${this.price}|"
          }
          Behaviors.same
        //End Make Bid

        //Init End Auction
        case EndAuction(auctionId) =>
          println("End Auction...")
          replicator.askUpdate(
            askReplyTo => Update(DataKey, LWWMap.empty[String, AuctionActorState], writeMajority, askReplyTo) {
              auctionStatePool => {
                auctionActorState = auctionStatePool.get(auctionId).head
                endAuction(auctionStatePool, auctionId)
              }
            },
            InternalEndAuctionResponse.apply(_,auctionId))
          Behaviors.same
        case InternalEndAuctionResponse(_: UpdateSuccess[_],auctionId) => {
          this.auctionActorState.currentWinner match {
            case null => {}
            case _ => {
              notifyWinner() //TODO
              notifyLosers(this.auctionActorState.currentWinner) //TODO
              resetVariables()
            };
          }
          //this.auctionSpawner ! FreeAuction(this.id)
          freeAuction(this.id)
          Behaviors.same
        }
        case InternalEndAuctionResponse(_: UpdateTimeout[_],auctionId) => Behaviors.same
        case InternalEndAuctionResponse(e: UpdateFailure[_],auctionId) => throw new IllegalStateException("Unexpected failure: " + e)
        //End End Auction

        //Init Delete Auction
        case DeleteAuction(replyTo) =>
          printf("\n\n\nCancelando subasta")
          this.timeout.cancel()
          selfNotifyCancelledAuction()
          freeAuction(this.id)
          replyTo ! s"Subasta ${this.auction.id} cancelada"
          Behaviors.same
        //End Delete Auction
      }

    }

  def startAuction(auctionStatePool:LWWMap[String, AuctionActorState], auctionId:String, newAuction: Auction) : LWWMap[String, AuctionActorState] = {
    val a = auctionStatePool.remove(node,auctionId)
    a :+ (auctionId -> AuctionActorState(newAuction, newAuction.basePrice, "",Set[String]()))
  }

  def endAuction(auctionStatePool:LWWMap[String, AuctionActorState], auctionId:String): LWWMap[String, AuctionActorState] = {
    auctionStatePool.remove(node,auctionId)
  }

    def updateWinner(buyerName : String):Unit = {
      this.currentWinner = buyerName
      this.buyers += buyerName
    }

  def notifyWinner() = {
    getOneActor(context, NotifierSpawnerActor.NotifierSpawnerServiceKey) ! NotifyWinner(this.auctionActorState.auction, this.auctionActorState.currentWinner)
  }

  def notifyLosers(currentWinner : String) = {
    val losers = this.auctionActorState.buyers.filter(b => ! b.equals(currentWinner))
    getOneActor(context, NotifierSpawnerActor.NotifierSpawnerServiceKey) ! NotifyLosers(this.auctionActorState.auction, losers)
  }

  def selfNotifyCancelledAuction() = {
    getOneActor(context, NotifierSpawnerActor.NotifierSpawnerServiceKey) ! NotifyCancellation(auction, buyers)
  }

  def selfNotifyNewPrice(newPrice:Double) = {
    getOneActor(context, NotifierSpawnerActor.NotifierSpawnerServiceKey) ! NotifyNewPrice(auction, buyers, newPrice)
  }

  /*def selfNotifyNewAuction(auctionId:String) = {
    getOneActor(context, NotifierSpawnerActor.NotifierSpawnerServiceKey) ! NotifyNewAuction(auction)
  }*/

  def freeAuction(auctionId:String) = {
    var message = "NotifierSpawner no disponible"
    getActors(context, AuctionSpawnerActor.AuctionSpawnerServiceKey).onComplete {
      case Success(actors) => {
        if (!actors.isEmpty) {
          actors.head ! FreeAuction(auctionId)
        } else {
          throw new IllegalStateException(message)
        }
      }
      case Failure(_) => throw new IllegalStateException(message)
    }
  }


}