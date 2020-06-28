package iasc.g4.routes

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.util.Timeout
import iasc.g4.actors.AuctionSpawnerActor.GetAuctions
import iasc.g4.actors.BuyersSubscriptorActor.GetBuyers
import iasc.g4.models.Models.Command

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

/**
 * Definición de rutas de la API HTTP con su vinculación a los diferentes actores
 * @param userSubscriber
 * @param auctionSpawner
 */
class Routes(userSubscriber: ActorRef[Command], auctionSpawner: ActorRef[Command])(implicit val system: ActorSystem[_]) {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import iasc.g4.models.Models._
  // If ask takes more time than this to complete the request is failed
  private implicit val timeout = Timeout.create(system.settings.config.getDuration("my-app.server.routes.ask-timeout"))

  /**
   * Route definitions for application
   */
  object BuyersRoutes {

    def getBuyers(): Future[Buyers] = {
      userSubscriber.ask(GetBuyers)
    }
    def createBuyer(newBuyer: Buyer): Future[String] = Future {
      // TODO integrar con actor
      "ok!"
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
      auctionSpawner.ask(GetAuctions)
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
        system.log.error("Unhandled error :(:", ex)
        complete(StatusCodes.InternalServerError, s"General error: ${ex.getMessage}")
    }

    handleExceptions(customHandler) {
      logRequestResult(("HTTP Request Result", Logging.InfoLevel)) {
        concat(AuctionRoutes.routes, BuyersRoutes.routes)
      }
    }
  }

}