package iasc.g4.actors

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import iasc.g4.CborSerializable

/**
 * Actor que maneja una subasta
 */
object AuctionActor {
  val WorkerServiceKey = ServiceKey[AuctionActor.TransformText]("Worker")

  sealed trait Command
  final case class TransformText(text: String, replyTo: ActorRef[TextTransformed]) extends Command with CborSerializable
  final case class TextTransformed(text: String) extends CborSerializable

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      // each worker registers themselves with the receptionist
      ctx.log.info("Registering myself with receptionist")
      ctx.system.receptionist ! Receptionist.Register(WorkerServiceKey, ctx.self)

      Behaviors.receiveMessage {
        case TransformText(text, replyTo) =>
          replyTo ! TextTransformed(text.toUpperCase)
          Behaviors.same
      }
    }
}
