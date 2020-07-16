package iasc.g4.actors

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import iasc.g4.models.Models.Command

/**
 * Encargado de asignar a actores Notifier la tarea de enviar una notificación, previamente obeniendo la lista de
 * compradores del BuyersSubscriptor
 */
object NotifierSpawnerActor {
  // TODO manter un pool fijo y reutilizarlos

  trait NotifierSpawnerCommand extends Command

  val NotifierSpawnerServiceKey = ServiceKey[NotifierSpawnerCommand]("NotifierSpawner")

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      ctx.log.info("Configurando BuyerSubscriptor")
      ctx.system.receptionist ! Receptionist.Register(NotifierSpawnerServiceKey, ctx.self)

      Behaviors.receiveMessage {
        case _ =>
          ctx.log.error("No implementado")
          Behaviors.same
      }
    }
}
