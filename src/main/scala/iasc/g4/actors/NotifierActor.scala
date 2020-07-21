package iasc.g4.actors
import iasc.g4.models.Models.{Command, OperationPerformed}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import HttpMethods._
import iasc.g4.models.Models.Buyer
import iasc.g4.models.Models.Auction
import iasc.g4.util.Util.makeHttpCall

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Actor que se encarga de enviar un mensaje de notificación a una lista de destinatarios
 */
object NotifierActor {
  final case class NewAuction(buyer: Buyer, auction:Auction)  extends Command

  def apply(): Behavior[Command] = Behaviors.setup {
    context =>
    Behaviors.receiveMessage {
      case NewAuction(buyer, auction) =>
        context.log.info("\n\n\n^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ por mandar mensaje! \n\n\n")
        makeHttpCall(s"http://${buyer.ip}/nuevaSubasta?id=${auction.id}");
        Behaviors.same
    }
  }
}



















