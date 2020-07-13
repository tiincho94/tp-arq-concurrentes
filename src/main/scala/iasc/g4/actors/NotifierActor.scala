package iasc.g4.actors
import iasc.g4.models.Models.{Command, OperationPerformed}


/**
 * Actor que se encarga de enviar un mensaje de notificación a una lista de destinatarios
 */
class NotifierActor {
  final case class Notification(whom: String, replyTo: ActorRef[Notified])
  final case class Notified(whom: String, owner: ActorRef[Notification])

  def apply(): Behavior[Notification] = Behaviors.receive { (ctx, message) =>
    ctx.log.info("New auction that may interest you!", message.whom)
    message.replyTo ! Notified(message.whom, ctx.self)
    Behaviors.same
  }
}
