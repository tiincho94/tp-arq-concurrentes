package iasc.g4.actors

import akka.actor.typed.Behavior
import iasc.g4.models.Models.Command
import iasc.g4.actors.NotifierActor
import iasc.g4.models.Models.OperationPerformed


import iasc.g4.models.Models.{Buyers}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import scala.util.{ Failure, Success }


/**
 * Encargado de asignar a actores Notifier la tarea de enviar una notificación, previamente obeniendo la lista de
 * compradores del BuyersSubscriptor
 */
object NotifierSpawnerActor {
//  // definición de commands (acciones a realizar)buyer:Buyer
  final case class NotifyBuyers(buyersNotified: Buyers, replyTo: ActorRef[String]) extends Command
//

  def apply(): Behavior[Command] =
    Behaviors.receiveMessage {
      case NotifyBuyers(buyersNotified, replyTo) =>
        implicit val system = ActorSystem()
        implicit val materializer = ActorMaterializer()
        // needed for the future flatMap/onComplete in the end
        implicit val executionContext = system.dispatcher

        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://localhost:8081/buyers"))

        responseFuture
          .onComplete {
            case Success(res) => replyTo ! "Notified!"
            case Failure(_)   => sys.error("something wrong")
          }



        Behaviors.same
    }

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      //#create-actors
      val notifier = context.spawn(NotifierActor(), "Notifier") //notifier
      //#create-actors

      Behaviors.notifyBuyers { newAuction, buyers =>

        // Como le notifico a ESE buyer interesado ?

        ctx.log.warn("Coso coso")
        Behaviors.same
//
//        val replyTo = context.spawn(NotifierBot(max = 3), newAuction.title)
//        notifier ! NotifierActor.Notification(newAuction.title, buyer)
//        Behaviors.same
      }
    }

}

// newAuction.title
// newAuction.owner
// newAuction.whom

//object NotifierBot {
//
//  def apply(max: Int): Behavior[NotifierActor.Notified] = {
//    bot(0, max)
//  }
//
//  private def bot(notificationingCounter: Int, max: Int): Behavior[NotifierActor.Notified] =
//    Behaviors.receive { (context, newAuction) =>
//      val n = notificationingCounter + 1
//      context.log.info("Notification {} for {}", n, newAuction.whom)
//      if (n == max) {
//        Behaviors.stopped
//      } else {
//        newAuction.owner ! NotifierActor.Notification(newAuction.whom, context.self)
//        bot(n, max)
//      }
//    }
//}
//#Notifier-bot
