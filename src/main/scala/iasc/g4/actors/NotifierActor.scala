package iasc.g4.actors
import iasc.g4.models.Models.{Command, OperationPerformed}

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import scala.util.{ Failure, Success }

/**
 * Actor que se encarga de enviar un mensaje de notificación a una lista de destinatarios
 */
object NotifierActor {
  final case class Notification(buyers: String, replyTo: ActorRef[String])  extends Command

  def apply(): Behavior[Command] = Behaviors.setup { context =>

    Behaviors.receiveMessage {
      case Notification(buyers, replyTo) =>
        context.log.info("***********************************************************************************************************************************************Got message {}", buyers)

        implicit val system = ActorSystem()
        implicit val materializer = ActorMaterializer()
        implicit val executionContext = system.dispatcher

        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://localhost:8081/buyers"))
        responseFuture
          .onComplete {
            case Success(res) => OperationPerformed("TBD")
            case Failure(_)   => sys.error("----------------------------------------------------------------------------------------------------------------something wrong")
          }
        Behaviors.same
    }
  }
}



















