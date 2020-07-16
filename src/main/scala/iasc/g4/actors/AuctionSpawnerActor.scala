package iasc.g4.actors

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import iasc.g4.models.Models.{Auction, Auctions, Command, OperationPerformed}

import scala.util.{Failure, Success}
import scala.concurrent.duration._
import akka.util.Timeout
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

import scala.util.Failure
import scala.util.Success

/**
 * Actor spawner de Auctions. Maneja nuevas subastas, su cancelación, etc
 */
object AuctionSpawnerActor {
  // TODO Revisar Escenario 7 de la consigna -> Debería no ser fijo, primer implementación sería crear
  //  libremente nuevas y en una segunda meter lo que pide el escenario 7

  trait AuctionSpawnerCommand extends Command

  val AuctionSpawnerServiceKey = ServiceKey[AuctionSpawnerCommand]("AuctionSpawner")

  // definición de commands (acciones a realizar)
  final case class GetAuctions(replyTo: ActorRef[Auctions]) extends AuctionSpawnerCommand
  final case class DeleteAuction(auctionId: String, replyTo: ActorRef[OperationPerformed]) extends AuctionSpawnerCommand
  final case class CreateAuction(newAuction: Auction, replyTo: ActorRef[OperationPerformed]) extends AuctionSpawnerCommand

  sealed trait Event
  private case object Tick extends Event
  private final case class WorkersUpdated(newWorkers: Set[ActorRef[Worker.TransformText]]) extends Event
  private final case class TransformCompleted(originalText: String, transformedText: String) extends Event
  private final case class JobFailed(why: String, text: String) extends Event

  // instanciación del objeto
  //def apply(): Behavior[Command] = auctions(Set.empty)

  def apply(): Behavior[Event] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      // subscribe to available workers
      val subscriptionAdapter = ctx.messageAdapter[Receptionist.Listing] {
        case Worker.WorkerServiceKey.Listing(workers) =>
          WorkersUpdated(workers)
      }
      ctx.system.receptionist ! Receptionist.Subscribe(Worker.WorkerServiceKey, subscriptionAdapter)
      auctions(Set.empty)
      timers.startTimerWithFixedDelay(Tick, Tick, 30.seconds)

      running(ctx, IndexedSeq.empty, jobCounter = 0)
    }
  }

  private def running(ctx: ActorContext[Event], workers: IndexedSeq[ActorRef[Worker.TransformText]], jobCounter: Int): Behavior[Event] =
    Behaviors.receiveMessage {
      case WorkersUpdated(newWorkers) =>
        ctx.log.info("List of services registered with the receptionist changed: {}", newWorkers)
        running(ctx, newWorkers.toIndexedSeq, jobCounter)
      case Tick =>
        if (workers.isEmpty) {
          ctx.log.warn("Got tick request but no workers available, not sending any work")
          Behaviors.same
        } else {
          // how much time can pass before we consider a request failed
          implicit val timeout: Timeout = 5.seconds
          val selectedWorker = workers(jobCounter % workers.size)
          ctx.log.info("Sending work for processing to {}", selectedWorker)
          val text = s"hello-$jobCounter"
          ctx.ask(selectedWorker, Worker.TransformText(text, _)) {
            case Success(transformedText) => TransformCompleted(transformedText.text, text)
            case Failure(ex) => JobFailed("Processing timed out", text)
          }
          running(ctx, workers, jobCounter + 1)
        }
      case TransformCompleted(originalText, transformedText) =>
        ctx.log.info("Got completed transform of {}: {}", originalText, transformedText)
        Behaviors.same

      case JobFailed(why, text) =>
        ctx.log.warn("Transformation of text {} failed. Because: {}", text, why)
        Behaviors.same

    }


  // comportamiento del actor
 private def auctions(auctions: Set[Auction]): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetAuctions(replyTo) =>
        replyTo ! Auctions(auctions)
        Behaviors.same
      case DeleteAuction(auctionId, replyTo) =>
        // TODO implementar
        replyTo ! OperationPerformed("TBD")
        Behaviors.same
      case CreateAuction(newAuction, replyTo) =>
        // TODO implementar
        replyTo ! OperationPerformed("TBD")
        Behaviors.same
    }
}
