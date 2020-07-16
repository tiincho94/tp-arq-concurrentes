package iasc.g4.util

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.ActorContext
import akka.util.Timeout

import scala.concurrent._

/**
 * Utilidades
 */
object Util {

  /**
   * @param ctx
   * @return Timeout a partir de la configuración del sistema
   */
  def getTimeout(ctx: ActorContext[_]): Timeout = {
    Timeout.create(ctx.system.settings.config.getDuration("akka.server.routes.ask-timeout"))
  }

  /**
   * Obtener el set de ActorRefs asociado a la ServiceKey indicada
   * @param ctx
   * @param key
   * @tparam T
   * @return
   */
  def getActors[T](ctx: ActorContext[_], key: ServiceKey[T]): Future[Set[ActorRef[T]]] = {
    ctx.system.receptionist.ask(Receptionist.Find(key))(getTimeout(ctx), ctx.system.scheduler).flatMap(listing =>
      Future.successful(listing.serviceInstances(key))
    )(ctx.executionContext)
  }
}