package iasc.g4.actors

import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, Behaviors}
import iasc.g4.actors.AuctionActor._
import iasc.g4.actors.AuctionSpawnerActor.{CreateAuction, FreeAuction}
import iasc.g4.models.Models.{Auction, Bid, Buyer, Buyers, Command, OperationPerformed}
import iasc.g4.util.Util.{getActors, _}

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import iasc.g4.actors.BuyersSubscriptorActor.{GetBuyer, GetBuyers}
import iasc.g4.actors.NotifierSpawnerActor.{NotifierSpawnerCommand, NotifyCancellation, NotifyLosers, NotifyNewAuction, NotifyNewAuction2, NotifyNewPrice, NotifyWinner}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


/**
 * Actor que maneja una subasta
 */
object AuctionActor {
  final case class StartAuction(auctionId:String, newAuction: Auction, replyTo: ActorRef[String]) extends Command
  final case class MakeBid(newBid:Bid, replyTo: ActorRef[String]) extends Command
  final case class Init(index:Long , auctionSpawner : ActorRef[AuctionSpawnerActor.AuctionSpawnerCommand]) extends Command
  final case class EndAuction() extends Command
  final case class DeleteAuction(replyTo:ActorRef[String]) extends Command

  def apply(index: Long,auctionActorServiceKey : ServiceKey[Command]): Behavior[Command] =
    Behaviors.setup(ctx => new AuctionActor(ctx,index,auctionActorServiceKey))
}

private class AuctionActor(
                            context: ActorContext[Command],
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

  //val AuctionActorServiceKey = ServiceKey[Command](s"AuctionActor$index")

  override def onMessage(msg: Command): Behavior[Command] =
    msg match {
      case StartAuction(auctionId,newAuction,replyTo) =>
        this.id = auctionId
        this.price = newAuction.basePrice
        this.duration = newAuction.duration
        this.tags = newAuction.tags
        this.article = newAuction.article
        this.auction = newAuction
        this.timeout = context.scheduleOnce(this.duration.seconds, context.self, EndAuction())
        selfNotifyNewAuction(this.auction.id) //TODO
        replyTo ! "Auction index: " + this.index.toString() + "\nTimeout is " + this.duration.toString()
        Behaviors.same
      case MakeBid(newBid,replyTo) =>
        if (newBid.price > this.price) {
          updateWinner(newBid.buyerName)
          this.price = newBid.price
          selfNotifyNewPrice(this.price) //TODO
          replyTo ! s"El nuevo precio es: |${this.price}| y el ganador por el momento es |${newBid.buyerName}|"
        } else {
          replyTo ! s"El precio enviado |${newBid.price}| puede ser menor que el establecido |${this.price}|"
        }
        Behaviors.same
      case EndAuction() =>
        this.currentWinner match {
          case null => {}
          case _ => {
            selfNotifyWinner() //TODO
            selfNotifyLosers(currentWinner) //TODO
          };
        }
        //this.auctionSpawner ! FreeAuction(this.id)
        freeAuction(this.id)
        Behaviors.same
      case DeleteAuction(replyTo) =>
        printf("\n\n\nCancelando subasta")
        this.timeout.cancel()
        selfNotifyCancelledAuction()
        freeAuction(this.id)
        Behaviors.same
    }

    def updateWinner(buyerName : String):Unit = {
      this.currentWinner = buyerName
      this.buyers += buyerName
    }

  def selfNotifyWinner() = {
    getOneActor(context, NotifierSpawnerActor.NotifierSpawnerServiceKey) ! NotifyWinner(auction, currentWinner)
  }

  def selfNotifyLosers(currentWinner : String) = {
    val losers = buyers.filter(b => !b.equals(currentWinner))
    getOneActor(context, NotifierSpawnerActor.NotifierSpawnerServiceKey) ! NotifyLosers(auction, losers)
  }

  def selfNotifyCancelledAuction() = {
    getOneActor(context, NotifierSpawnerActor.NotifierSpawnerServiceKey) ! NotifyCancellation(auction, buyers)
  }

  def selfNotifyNewPrice(newPrice:Double) = {
    getOneActor(context, NotifierSpawnerActor.NotifierSpawnerServiceKey) ! NotifyNewPrice(auction, buyers, newPrice)
  }

  def selfNotifyNewAuction(auctionId:String) = {
    getOneActor(context, NotifierSpawnerActor.NotifierSpawnerServiceKey) ! NotifyNewAuction2(auction)
  }

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