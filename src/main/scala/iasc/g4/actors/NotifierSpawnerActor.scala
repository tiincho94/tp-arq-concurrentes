package iasc.g4.actors

import iasc.g4.models.Models.Command
import iasc.g4.actors.NotifierActor
import iasc.g4.models.Models.OperationPerformed
import iasc.g4.models.Models.Buyers

import akka.actor.typed.scaladsl.{ Behaviors, Routers }
import scala.concurrent.Future
import scala.util.{Failure, Success}
import akka.actor.typed.{ Behavior, SupervisorStrategy }
import akka.actor.typed.{ActorRef, Behavior}

/**
 * Encargado de asignar a actores Notifier la tarea de enviar una notificación, previamente obeniendo la lista de
 * compradores del BuyersSubscriptor
 */
object NotifierSpawnerActor {
//  // definición de commands (acciones a realizar)buyer:Buyer
  final case class NotifyBuyers(buyersNotified: Buyers, replyTo: ActorRef[String]) extends Command
//

//  def apply(): Behavior[Command] =
//    Behaviors.receiveMessage {
//      case NotifyBuyers(buyersNotified, replyTo) =>
//        implicit val system = ActorSystem()
//        implicit val materializer = ActorMaterializer()
//        // needed for the future flatMap/onComplete in the end
//        implicit val executionContext = system.dispatcher
//
//        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://localhost:8081/buyers"))
//
//        responseFuture
//          .onComplete {
//            case Success(res) => replyTo ! "Notified!"
//            case Failure(_)   => sys.error("something wrong")
//          }
//        Behaviors.same
//    }


  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>

      val pool = Routers.pool(poolSize = 5)(
        // make sure the workers are restarted if they fail
        Behaviors.supervise(NotifierActor()).onFailure[Exception](SupervisorStrategy.restart))
      val router = ctx.spawn(pool, "notifier-pool")

      Behaviors.receiveMessage {
        case NotifyBuyers(buyersNotified, replyTo) =>

          (0 to 5).foreach { n =>
            router ! NotifierActor.Notification(buyersNotified.buyers.size.toString, replyTo)
          }

          replyTo ! "Listo!"
          Behaviors.same
      }
    }

}
















