package iasc.g4.actors

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
import iasc.g4.actors.NotifierSpawnerActor.{NotifierSpawnerCommand, NotifyNewAuction}

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
  var currentWinner: Buyer = null
  var buyers = Set[Buyer]()
  var auction: Auction = null

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
        context.scheduleOnce(this.duration.seconds, context.self, EndAuction())
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

  def getBuyers(): Future[Buyers] = {
    getActors(context, BuyersSubscriptorActor.BuyersSubscriptorServiceKey).flatMap(actors =>
      if (!actors.isEmpty) {
        actors.head.ask(GetBuyers(this.auction.tags,_))(getTimeout(context), context.system.scheduler)
      } else {
        Future.failed(new IllegalStateException("BuyersSubscriptor no disponible"))
      }
    )
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

  def selfNotifyWinner() = {
    makeHttpCall(s"http://${this.currentWinner.ip}/subastaGanada?id=${this.id}")
  }

  def selfNotifyLosers(currentWinner:Buyer) = {
    this.buyers.foreach(
      buyer =>
        if(!buyer.equals(currentWinner)) {
          makeHttpCall(s"http://${buyer.ip}/subastaPerdida?id=${this.auction.id}")
        }
    )
  }

  def selfNotifyNewPrice(newPrice:Double) = {
    this.buyers.foreach(
      buyer => makeHttpCall(s"http://${buyer.ip}/nuevoPrecio?id=${this.auction.id}&precio=${newPrice}")
    )
  }

  def selfNotifyNewAuction(auctionId:String) = {
    getActors(context, NotifierSpawnerActor.NotifierSpawnerServiceKey).onComplete {
      case Success(actors) => {
        if (!actors.isEmpty) {
          getBuyers().onComplete {
            case Success(remoteBuyers) => {
              remoteBuyers.buyers.foreach(
                buyer => makeHttpCall(s"http://${buyer.ip}/nuevaSubasta?id=${auctionId}")
              )
            }
            case Failure(_) => {this.context.log.error("Imposible obtener buyers")}
          }
          //actors.head ! NotifyNewAuction(this.buyers, this.auction)
        } else {
          throw new IllegalStateException("NotifierSpawner no disponible")
        }
      }
      case Failure(_) => throw new IllegalStateException("NotifierSpawner no disponible")
    }
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