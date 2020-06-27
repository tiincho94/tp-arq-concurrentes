package tp

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object TP extends App {

  import Demo._
  val system_supervisor: ActorSystem[Demo.SayHello] = ActorSystem(Demo.greeter, "BS");
  system_supervisor ! SayHello("Lucas");
  system_supervisor ! SayHello("Sarasa");
}

/**
 * Actor para pruebas, TODO eliminar
 */
object Demo {

  final case class SayHello(name: String)

  val greeter: Behavior[SayHello] = Behaviors.receive { (context, message) =>
    println(s"Hello ${message.name}");
    Behaviors.same;
  }
}

/**
 * Encargado de suscribir nuevos compradores manteniendo la lista de los mismos.</br>
 * Permite consultar la lista de compradores para un set de tags
 */
case class BuyersSubscriptor() {
  // TODO
}

/**
 * Encargado de asignar a actores Notifier la tarea de enviar una notificación, previamente obeniendo la lista de
 * compradores del BuyersSubscriptor
 */
object NotifierSpawner {
  // TODO manter un pool fijo y reutilizarlos
}

/**
 * Actor que se encarga de enviar un mensaje de notificación a una lista de destinatarios
 */
class Notifier {

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
