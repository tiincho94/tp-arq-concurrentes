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

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case NewAuction(buyer, auction) =>
          ctx.log.info(s"Avisando de nueva auction a buyer ${buyer.name}")
          makeHttpCall(s"http://${buyer.ip}/nuevaSubasta?id=${auction.id}");
          Behaviors.same
      }
  }
}



















