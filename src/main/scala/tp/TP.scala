package tp

import akka.actor.typed.scaladsl.Behaviors

object TP extends App {

  val rootBehavior = Behaviors.setup[Nothing] { context =>
    val buyersSubscriptorActor = context.spawn(BuyersSubscriptor(), "BuyerSubscriptor")
    // context.watch(buyersSubscriptorActor)
    buyersSubscriptorActor.

    Behaviors.empty
  }

  //implicit val systemSupervisor = ActorSystem[Nothing](rootBehavior);
  val greeterMain: ActorSystem[GreeterMain.SayHello] = ActorSystem(BuyersSubscriptor(), "buyersSubscriptor")

  case class Demo() {
    final case class SayHello(name: String)

    def apply(): Behavior[SayHello] =
      Behaviors.setup { context =>
        val greeter = context.spawn(Greeter(), "greeter")
        Behaviors.receiveMessage { message =>
          greeter ! Greeter.Greet(message.name, replyTo)
          Behaviors.same
        }
      }
  }

  /**
   * Encargado de suscribir nuevos compradores manteniendo la lista de los mismos.</br>
   * Permite consultar la lista de compradores para un set de tags
   */
  case class BuyersSubscriptor() {

    final case class Solicitud(buyer: String, replyTo: ActorRef[ConfirmacionSolicitud])
    final case class ConfirmacionSolicitud(buyer: String, from: ActorRef[Solicitud])

    def apply(): Behavior[Solicitud] = Behaviors.receive { (context, message) =>
      context.log.info("Suscribiendo a {}!", message.buyer)
      message.replyTo ! ConfirmacionSolicitud(message.buyer, context.self)
      Behaviors.same
    }
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

}