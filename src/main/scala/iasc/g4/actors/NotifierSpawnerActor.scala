package iasc.g4.actors

import akka.actor.typed.Behavior
import iasc.g4.Models.Command

/**
 * Encargado de asignar a actores Notifier la tarea de enviar una notificación, previamente obeniendo la lista de
 * compradores del BuyersSubscriptor
 */
object NotifierSpawnerActor {
  // TODO manter un pool fijo y reutilizarlos
  def apply(): Behavior[Command] = null
}
