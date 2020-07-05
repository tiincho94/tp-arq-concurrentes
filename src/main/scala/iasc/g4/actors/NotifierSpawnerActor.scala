package iasc.g4.actors

import akka.actor.typed.Behavior
import iasc.g4.models.Models.Command
import iasc.g4.actors.NotifierActor
import iasc.g4.models.Models.OperationPerformed

/**
 * Encargado de asignar a actores Notifier la tarea de enviar una notificación, previamente obeniendo la lista de
 * compradores del BuyersSubscriptor
 */
object NotifierSpawnerActor {
  // definición de commands (acciones a realizar)
  final case class NotifyBuyers(buyersNotified: ActorRef[Buyers]) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      //#create-actors
      val notifier = context.spawn(NotifierActor(), "Notifier") //notifier
      //#create-actors

      Behaviors.receiveMessage { message =>
        //#create-actors
        val replyTo = context.spawn(NotifierBot(max = 3), message.name) //notified
        //#create-actors
        notifier ! NotifierActor.Notification(message.name, replyTo)
        Behaviors.same
      }
    }

  // instanciación del objeto
  def apply(): Behavior[Command] = notifiers(Set.empty)

  // comportamiento del actor
  private def notifiers(users: Set[Buyer]): Behavior[Command] =
    Behaviors.receiveMessage {
      case NotifyBuyers(replyTo) =>
        replyTo ! OperationPerformed("TBD")
        Behaviors.same
    }
}

//#Notifier-bot
object NotifierBot {

  def apply(max: Int): Behavior[NotifierActor.Notified] = {
    bot(0, max)
  }

  private def bot(notificationingCounter: Int, max: Int): Behavior[NotifierActor.Notified] =
    Behaviors.receive { (context, message) =>
      val n = notificationingCounter + 1
      context.log.info("Notification {} for {}", n, message.whom)
      if (n == max) {
        Behaviors.stopped
      } else {
        message.from ! NotifierActor.Notification(message.whom, context.self)
        bot(n, max)
      }
    }
}
//#Notifier-bot
