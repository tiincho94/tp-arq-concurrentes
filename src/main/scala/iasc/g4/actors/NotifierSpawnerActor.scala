package iasc.g4.actors

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}

import iasc.g4.models.Models.Command
import iasc.g4.models.Models.Buyer
import iasc.g4.models.Models.Auction

import akka.actor.typed.scaladsl.{ Behaviors, Routers }
import akka.actor.typed.{ Behavior, SupervisorStrategy }
import akka.actor.typed.{ActorRef, Behavior}

/**
 * Encargado de asignar a actores Notifier la tarea de enviar una notificación, previamente obeniendo la lista de
 * compradores del BuyersSubscriptor
 */
object NotifierSpawnerActor {

  // TODO manter un pool fijo y reutilizarlos

  trait NotifierSpawnerCommand extends Command

  final case class NotifyNewAuction(buyersNotified: Set[Buyer], auction: Auction) extends NotifierSpawnerCommand

  val NotifierSpawnerServiceKey = ServiceKey[NotifierSpawnerCommand]("NotifierSpawner")

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      ctx.log.info("Configurando NotifierSpawnerActor")
      ctx.system.receptionist ! Receptionist.Register(NotifierSpawnerServiceKey, ctx.self)

      val pool = Routers.pool(poolSize = 5)(
        Behaviors.supervise(NotifierActor()).onFailure[Exception](SupervisorStrategy.restart)
      )
      val router = ctx.spawn(pool, "notifier-pool")

      Behaviors.receiveMessage {
        case NotifyNewAuction(buyersNotified, auction) =>
          printf(s"Notificando new auction a ${buyersNotified.size} buyers")
          for (buyer <- buyersNotified){
            router ! NotifierActor.NewAuction(buyer, auction)
          }
          Behaviors.same
      }
    }
}
















