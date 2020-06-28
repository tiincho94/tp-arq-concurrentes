package iasc.g4.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import iasc.g4.Models.{Buyer, Buyers, Command, OperationPerformed}

/**
 * Actor que suscribe un nuevo comprador y mantiene una lista con los mismos.</br>
 * También permite consultar la lista de compradores
 */
object BuyersSubscriptorActor {
  // definición de commands (acciones a realizar)
  final case class GetBuyers(replyTo: ActorRef[Buyers]) extends Command

  // instanciación del objeto
  def apply(): Behavior[Command] = buyers(Set.empty)

  // comportamiento del actor
  private def buyers(users: Set[Buyer]): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetBuyers(replyTo) =>
        replyTo ! Buyers(Set.empty)
        Behaviors.same
    }
}
