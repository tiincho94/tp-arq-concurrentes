package iasc.g4.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, Behaviors}
import iasc.g4.actors.AuctionActor._
import iasc.g4.actors.AuctionSpawnerActor.FreeAuction
import iasc.g4.models.Models.{Auction, Bid, Buyer, Buyers, Command, OperationPerformed}
import iasc.g4.util.Util.{getActors, _}

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import iasc.g4.actors.BuyersSubscriptorActor.{GetBuyer, GetBuyers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


/**
 * Actor que maneja una subasta
 */
object AuctionActor {
  final case class StartAuction(auctionId:String, newAuction: Auction, replyTo: ActorRef[String]) extends Command
  final case class MakeBid(newBid:Bid, replyTo: ActorRef[String]) extends Command
  final case class Init(index:Long,auctionSpawner : ActorRef[AuctionSpawnerActor.AuctionSpawnerCommand]) extends Command
  final case class EndAuction() extends Command

  def apply(index: Long, auctionSpawner: ActorRef[Command]): Behavior[Command] =
    Behaviors.setup(ctx => new AuctionActor(ctx,index,auctionSpawner))
}

private class AuctionActor(
                            context: ActorContext[Command],
                            index:Long,
                            auctionSpawner:ActorRef[AuctionSpawnerActor.AuctionSpawnerCommand]
                          ) extends AbstractBehavior[Command](context) {

  var id : String = ""
  var price : Double = 0.0
  var duration : Long = 0
  var tags = Set[String]()
  var article : String = ""
  var currentWinner: Buyer = null
  var buyers = Set[Buyer]()

  override def onMessage(msg: Command): Behavior[Command] =
    msg match {
      case StartAuction(auctionId,newAuction,replyTo) =>
        this.id = auctionId
        this.price = newAuction.basePrice
        this.duration = newAuction.duration
        this.tags = newAuction.tags
        this.article = newAuction.article
        context.scheduleOnce(this.duration.seconds, context.self, EndAuction())
        replyTo ! "Auction index: " + this.index.toString() + "\nTimeout is " + this.duration.toString()
        Behaviors.same
      case MakeBid(newBid,replyTo) =>
        updateWinner(newBid.buyerName)
        if (newBid.price > this.price) {
          this.price = newBid.price
          replyTo ! "El nuevo precio es: $"+this.price+" y el ganador actual es "+newBid.buyerName
        } else {
          replyTo ! "El precio enviado no puede ser menor que el establecido"
        }
        Behaviors.same
      case EndAuction() =>
        this.currentWinner match {
          case null => {}
          case _ => makeHttpCall(this.currentWinner.ip+"?id="+this.id);
        }
        this.auctionSpawner ! FreeAuction(this.id)
        Behaviors.same
    }

    def updateWinner(buyerName : String):Unit = {
      this.buyers.find(localBuyer => localBuyer.name == buyerName) match {
        case Some(localBuyer) =>
          this.currentWinner=localBuyer
        case None => {
          getBuyer(buyerName).onComplete {
            case Success(remoteBuyer) => {
              this.buyers += remoteBuyer
              this.currentWinner = remoteBuyer
            }
            case Failure(_) => throw new IllegalArgumentException("Buyer no encontrado")
          }
        }
      }
    }

    def getBuyer(name:String): Future[Buyer] = {
      getActors(context, BuyersSubscriptorActor.BuyersSubscriptorServiceKey).flatMap(actors =>
        if (!actors.isEmpty) {
          actors.head.ask(GetBuyer(name,_))(getTimeout(context), context.system.scheduler)
        } else {
          Future.failed(new IllegalStateException("BuyersSubscriptor no disponible"))
        }
      )
    }
}