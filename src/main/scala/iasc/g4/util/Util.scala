package iasc.g4.util

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import iasc.g4.models.Models.OperationPerformed
import scala.concurrent._
import scala.util.{Failure, Success}

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

  /**
   * Obtener el primer actor disponible para la key indicada
   * @param ctx
   * @param key
   * @tparam T
   * @return
   */
  def getOneActor[T](ctx: ActorContext[_], key: ServiceKey[T]): ActorRef[T] = {
    val actors : Set[ActorRef[T]] = Await.result(getActors(ctx, key), getTimeout(ctx).duration)
    if (actors.isEmpty) throw new IllegalStateException(s"Actor no disponible para key ${key.id}")
    actors.head
  }

  def makeHttpCall(_uri : String):Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = _uri))
    responseFuture
      .onComplete {
        case Success(_) => OperationPerformed("Http call ok")
        case Failure(err)   => system.log.error("Error haciendo http call", err)
      }
  }


}