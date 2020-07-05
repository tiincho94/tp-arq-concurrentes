package iasc.g4.actors
import iasc.g4.models.Models.{Command, OperationPerformed}


/**
 * Actor que se encarga de enviar un mensaje de notificación a una lista de destinatarios
 */
class NotifierActor {
  final case class Notification(whom: String, replyTo: ActorRef[Notified])
  final case class Notified(whom: String, from: ActorRef[Notification])

  def apply(): Behavior[Notification] = Behaviors.receive { (context, message) =>
    context.log.info("Hello {}!", message.whom)
    //#Notifier-send-messages
    message.replyTo ! Notified(message.whom, context.self)
    //#Notifier-send-messages
    Behaviors.same
  }
}
