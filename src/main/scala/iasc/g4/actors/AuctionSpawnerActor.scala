package iasc.g4.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import iasc.g4.Models.{Auction, Auctions, Command, OperationPerformed}

/**
 * Actor spawner de Auctions. Maneja nuevas subastas, su cancelación, etc
 */
object AuctionSpawnerActor {
  // TODO Revisar Escenario 7 de la consigna -> Debería no ser fijo, primer implementación sería crear
  //  libremente nuevas y en una segunda meter lo que pide el escenario 7

  // definición de commands (acciones a realizar)
  final case class GetAuctions(replyTo: ActorRef[Auctions]) extends Command
  final case class DeleteAuction(auctionId: String, replyTo: ActorRef[OperationPerformed]) extends Command
  final case class CreateAuction(newAuction: Auction, replyTo: ActorRef[OperationPerformed]) extends Command

  // instanciación del objeto
  def apply(): Behavior[Command] = auctions(Set.empty)

  // comportamiento del actor
  private def auctions(users: Set[Auction]): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetAuctions(replyTo) =>
        replyTo ! Auctions(Set.empty)
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
