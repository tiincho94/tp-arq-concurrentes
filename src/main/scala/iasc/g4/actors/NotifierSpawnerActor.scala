package iasc.g4.actors

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors

import iasc.g4.models.Models.Command
import iasc.g4.actors.NotifierActor
import iasc.g4.models.Models.OperationPerformed
import iasc.g4.models.Models.Buyers
import iasc.g4.models.Models.Auction

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

  // TODO manter un pool fijo y reutilizarlos

  trait NotifierSpawnerCommand extends Command

  final case class NotifyBuyers(buyersNotified: Buyers, auction: Auction, replyTo: ActorRef[String]) extends NotifierSpawnerCommand

  val NotifierSpawnerServiceKey = ServiceKey[NotifierSpawnerCommand]("NotifierSpawner")

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      ctx.log.info("Configurando BuyerSubscriptor")
      ctx.system.receptionist ! Receptionist.Register(NotifierSpawnerServiceKey, ctx.self)

      val pool = Routers.pool(poolSize = 5)(
        Behaviors.supervise(NotifierActor()).onFailure[Exception](SupervisorStrategy.restart))
      val router = ctx.spawn(pool, "notifier-pool")

      Behaviors.receiveMessage {
        case NotifyBuyers(buyersNotified, auction, replyTo) =>
          for (buyer <- buyersNotified.buyers){
            router ! NotifierActor.Notification(buyer, auction, replyTo)
          }

          replyTo ! "Listo!"
          Behaviors.same
      }
    }
}
















