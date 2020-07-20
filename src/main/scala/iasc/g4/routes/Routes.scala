package iasc.g4.routes

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.ActorContext
import akka.event.Logging
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import iasc.g4.actors.BuyersSubscriptorActor
import iasc.g4.actors.NotifierSpawnerActor
import iasc.g4.actors.BuyersSubscriptorActor.{CreateBuyer, GetBuyers}
import iasc.g4.actors.NotifierSpawnerActor.{NotifyBuyers}
import iasc.g4.models.Models.Command

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

/**
 * Definición de rutas de la API HTTP con su vinculación a los diferentes actores
 */
class Routes(context: ActorContext[_]) {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import iasc.g4.util.Util._
  import iasc.g4.models.Models._
  private implicit val timeout = getTimeout(context)

  /**
   * Route definitions for application
   */
  object BuyersRoutes {

    def getBuyers(): Future[Buyers] = {
      getActors(context, BuyersSubscriptorActor.BuyersSubscriptorServiceKey).flatMap(actors =>
        if (!actors.isEmpty) {
          actors.head.ask(GetBuyers)(timeout, context.system.scheduler)
        } else {
          Future.failed(new IllegalStateException("BuyersSubscriptor no disponible"))
        }
      )
    }

    def createBuyer(newBuyer: Buyer): Future[String] = {
      getActors(context, BuyersSubscriptorActor.BuyersSubscriptorServiceKey).flatMap(actors =>
        actors.head.ask(CreateBuyer(newBuyer,_))(timeout, context.system.scheduler)
      )
    }

    val routes: Route = pathPrefix("buyers") {
      concat(
        pathEnd(
          concat(
            get {
              complete(getBuyers())
            },
            post {
              entity(as[Buyer]) { newBuyer =>
                complete(StatusCodes.Created, createBuyer(newBuyer))
              }
            }
          )
        )
      )
    }
  }

  object AuctionRoutes {
    def getAuctions: Future[Auctions] = {
      // TODO completar
      Future.successful(Auctions(Set.empty))
    }
    def getAuction(id: String): Option[Auction] = {
      // TODO integrar con actor
      Option.empty
    }
    def createAuction(newAuction: Auction): Future[String] = Future {
      // TODO integrar con actor
      "666"
    }
    def cancelAuction(auctionId: String): Future[String] = Future {
      // TODO integrar con actor
      "Ok!"
    }

    val routes: Route = pathPrefix("auctions") {
      concat(
        pathEnd(
          get {
            complete(getAuctions)
          }
        ),
        path(Segment) { auctionId =>
          concat(
            get {
              complete(getAuction(auctionId))
            },
            post {
              entity(as[Auction]) { newAuction =>
                complete(StatusCodes.Created, createAuction(newAuction))
              }
            },
            delete {
              complete(cancelAuction(auctionId))
            }
          )
        }
      )
    }
  }



  /**
   * @return rutas completas con manejo de excepciones general
   */
  def routes(): Route = {

    val customHandler: ExceptionHandler = ExceptionHandler {
      case ex: Exception =>
        context.system.log.error("Unhandled error :(:", ex)
        complete(StatusCodes.InternalServerError, s"General error: ${ex.getMessage}")
    }

    handleExceptions(customHandler) {
      logRequestResult(("HTTP Request Result", Logging.InfoLevel)) {
        concat(AuctionRoutes.routes, BuyersRoutes.routes)
      }
    }
  }

}