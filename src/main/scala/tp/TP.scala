package tp
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import tp.NotifierSpawner.NewNotification

//#Notifier-actor
object Notifier {
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
//#Notifier-actor

//#Notifier-bot
object NotifierBot {

  def apply(max: Int): Behavior[Notifier.Notified] = {
    bot(0, max)
  }

  private def bot(notificationingCounter: Int, max: Int): Behavior[Notifier.Notified] =
    Behaviors.receive { (context, message) =>
      val n = notificationingCounter + 1
      context.log.info("Notification {} for {}", n, message.whom)
      if (n == max) {
        Behaviors.stopped
      } else {
        message.from ! Notifier.Notification(message.whom, context.self)
        bot(n, max)
      }
    }
}
//#Notifier-bot

//#Notifier-main
object NotifierSpawner {

  final case class NewNotification(name: String) //NewNotification

  def apply(): Behavior[NewNotification] =
    Behaviors.setup { context =>
      //#create-actors
      val notifier = context.spawn(Notifier(), "Notifier") //notifier
      //#create-actors

      Behaviors.receiveMessage { message =>
        //#create-actors
        val replyTo = context.spawn(NotifierBot(max = 3), message.name) //notified
        //#create-actors
        notifier ! Notifier.Notification(message.name, replyTo)
        Behaviors.same
      }
    }
}
//Acá empieza! Es el main (System)

object TP extends App {

  //#actor-system
  val notifierSpawner: ActorSystem[NotifierSpawner.NewNotification] = ActorSystem(NotifierSpawner(), "NotifierSpawner")
  //#actor-system

  //#main-send-messages
  notifierSpawner ! NewNotification("Auction")
  //#main-send-messages
}

/**
 * Encargado de suscribir nuevos compradores manteniendo la lista de los mismos.</br>
 * Permite consultar la lista de compradores para un set de tags
 */
case class BuyersSubscriptor() {
  // TODO
}

/**
 * Actor spawner de Auctions para manejo de una nueva subasta
 */
object AuctionSpawner {
  // TODO manter un pool fijo y reutilizarlos
}

/**
 * Actor que maneja una subasta
 */
class Auction {

}
