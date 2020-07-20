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

import scala.concurrent.Future
import scala.util.{ Failure, Success }

/**
 * Actor que se encarga de enviar un mensaje de notificación a una lista de destinatarios
 */
object NotifierActor {
  final case class NewAuction(buyer: Buyer, auction:Auction)  extends Command

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    context.log.info("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ start! ")

    Behaviors.receiveMessage {
      case NewAuction(buyer, auction) =>
        printf("*********************************************************************************************************************************************** Numero {}", buyer)

        implicit val system = ActorSystem()
        implicit val materializer = ActorMaterializer()
        implicit val executionContext = system.dispatcher

        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = buyer.ip + "/buyers"))

        println("\n\n\n\n\n\n\n\n buyers")

        responseFuture
          .onComplete {
            case Success(res) => println("\n\n\n\n\n\n\n\nASDF SE COMUNICA BIEN")
            case Failure(_)   => sys.error("----------------------------------------------------------------------------------------------------------------something wrong")
          }
        Behaviors.same
    }
  }
}



















