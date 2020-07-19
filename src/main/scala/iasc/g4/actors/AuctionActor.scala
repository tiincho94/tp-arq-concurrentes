package iasc.g4.actors

import scala.util.{Failure, Success}
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import iasc.g4.actors.BuyersSubscriptorActor.GetBuyers
import iasc.g4.models.Models.{Auction, Bid, Buyers, Command, OperationPerformed}
import iasc.g4.util.Util.{getActors, getTimeout}

import scala.concurrent.duration._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import cats.Inject
import iasc.g4.CborSerializable
import iasc.g4.actors.AuctionActor._
import iasc.g4.actors.AuctionSpawnerActor.{AuctionSpawnerCommand, Event, FreeAuction}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future
import iasc.g4.util.Util._

/**
 * Actor que maneja una subasta
 */
object AuctionActor {
  // para definir "variables de clase"
  final case class TransformText(text: String, replyTo: ActorRef[TextTransformed]) extends Command
  final case class TextTransformed(text: String) extends CborSerializable
  final case class StartAuction(auctionId:String, newAuction: Auction, replyTo: ActorRef[String]) extends Command
  final case class MakeBid(newBid:Bid, replyTo: ActorRef[String]) extends Command
  final case class Init(index:Long,auctionSpawner : ActorRef[AuctionSpawnerActor.AuctionSpawnerCommand]) extends Command
  final case class EndAuction() extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup(ctx => new AuctionActor(ctx))
}

private class AuctionActor(context: ActorContext[Command]) extends AbstractBehavior[Command](context) {

  var id : String = ""
  var index : Long = 0L
  var price : Double = 0.0
  var duration : Long = 0
  var tags = Set[String]()
  var article : String = ""
  var highestBidder : String = "http://localhost:8080/subastaGanada?id="
  var auctionSpawner : ActorRef[AuctionSpawnerActor.AuctionSpawnerCommand] = _

  override def onMessage(msg: Command): Behavior[Command] =
    Behaviors.setup { ctx =>
      // each worker registers themselves with the receptionist
      // printf("Registering myself with receptionist")
      // ctx.system.receptionist ! Receptionist.Register(AuctionActorServiceKey, ctx.self)
      Behaviors.receiveMessage {
        case Init(index,auctionSpawner) =>
          printf("Iniciando AuctionActor")
          this.index = index
          this.auctionSpawner = auctionSpawner
          Behaviors.same
        case TransformText(text, replyTo) =>
          replyTo ! TextTransformed(text.toUpperCase)
          Behaviors.same
        case StartAuction(auctionId,newAuction,replyTo) =>
          this.id = auctionId
          this.price = newAuction.basePrice
          this.duration = newAuction.duration
          this.tags = newAuction.tags
          this.article = newAuction.article
          ctx.scheduleOnce(this.duration.seconds,ctx.self,EndAuction())
          replyTo ! "Auction index: "+this.index.toString()+"\nTimeout is "+this.duration.toString()
          Behaviors.same
        case MakeBid(newBid,replyTo) =>
          //TODO: implementar
          Behaviors.same
        case EndAuction() =>
          makeHttpCall(this.highestBidder+this.id);
          this.auctionSpawner ! FreeAuction(this.id)
          Behaviors.same
      }
    }
}