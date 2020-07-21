package iasc.g4.actors
import iasc.g4.models.Models.{Command}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior}
import iasc.g4.models.Models.Buyer
import iasc.g4.models.Models.Auction
import iasc.g4.util.Util.makeHttpCall

/**
 * Actor que se encarga de enviar un mensaje de notificación a una lista de destinatarios
 */
object NotifierActor {

  final case class NewAuction(buyer: Buyer, auction:Auction) extends Command
  final case class Winner(buyer: Buyer, auction:Auction) extends Command
  final case class Looser(buyer: Buyer, auction:Auction) extends Command
  final case class NewPrice(buyer: Buyer, newPrice: Double, auction:Auction) extends Command
  final case class Cancellation(buyer: Buyer, auction:Auction) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case NewAuction(buyer, auction) =>
          ctx.log.info(s"Avisando de nueva auction a buyer ${buyer.name}")
          makeHttpCall(s"http://${buyer.ip}/nuevaSubasta?id=${auction.id}");
          Behaviors.same
        case Winner(buyer, auction) =>
          ctx.log.info(s"Avisando a ganador ${buyer.name} de auction ${auction.id}")
          makeHttpCall(s"http://${buyer.ip}/subastaGanada?id=${auction.id}")
          Behaviors.same
        case Looser(buyer, auction) =>
          ctx.log.info(s"Avisando a looser ${buyer.name}")
          makeHttpCall(s"http://${buyer.ip}/subastaPerdida?id=${auction.id}")
          Behaviors.same
        case NewPrice(buyer, newPrice, auction) =>
          ctx.log.info(s"Avisando nuevo precio a ${buyer.name}")
          makeHttpCall(s"http://${buyer.ip}/nuevoPrecio?id=${auction.id}&precio=$newPrice")
          Behaviors.same
        case Cancellation(buyer, auction) =>
          ctx.log.info(s"Avisando de cancelación a ${buyer.name}")
          makeHttpCall(s"http://${buyer.ip}/subastaCancelada?id=${auction.id}")
          Behaviors.same
      }
  }
}



















