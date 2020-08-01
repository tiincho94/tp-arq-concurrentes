package iasc.g4.util

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.ActorContext
import scala.concurrent.duration._
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

  implicit val clientSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = clientSystem.dispatcher
  /**
   * tiempo de espera a que aparezca un actor en el receptionist
   */
  val actorWait = 2000

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
  def getOneActor[T](ctx: ActorContext[_], key: ServiceKey[T], retries: Int = 0): Option[ActorRef[T]] = {
    var triesLeft = retries
    while(triesLeft >= 0) {
      val actors : Set[ActorRef[T]] = Await.result(getActors(ctx, key), getTimeout(ctx).duration)
      if (!actors.isEmpty) {
        return Option(actors.head)
      } else if (triesLeft > 0) {
        ctx.log.debug(s"Esperando por un actor para la key ${key.id}...")
        Thread.sleep(actorWait)
      }
      triesLeft-=1;
    }
    ctx.log.debug(s"No se pudo obtener un actor para la key ${key.id}")
    Option.empty
  }

  def makeHttpCall(_uri : String):Unit = {
    val responseFuture: Future[HttpResponse] = Http(clientSystem).singleRequest(HttpRequest(uri = _uri))
    responseFuture
      .onComplete {
        case Success(_) => OperationPerformed("Http call ok")
        case Failure(err) => clientSystem.log.error(err,"Error haciendo http call")
      }
  }


}