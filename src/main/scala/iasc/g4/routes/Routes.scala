package iasc.g4.routes

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.ActorContext
import akka.event.Logging
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import iasc.g4.actors.AuctionSpawnerActor.{CreateAuction, MakeBid}
import iasc.g4.actors.{AuctionSpawnerActor, BuyersSubscriptorActor}
import iasc.g4.actors.BuyersSubscriptorActor.{CreateBuyer, GetBuyers}
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

    def getBuyers(tags: Set[String]): Future[Buyers] = {
      getActors(context, BuyersSubscriptorActor.BuyersSubscriptorServiceKey).flatMap(actors =>
        if (!actors.isEmpty) {
          actors.head.ask(GetBuyers(tags, _))(timeout, context.system.scheduler)
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
              parameters("tags".as[String].*) { tags =>
                complete(getBuyers(tags.toSet))
              }
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

    def makeBid(auctionId:String,newBid:Bid):Future[String] = {
      getActors(context, AuctionSpawnerActor.AuctionSpawnerServiceKey).flatMap(actors =>
        if (!actors.isEmpty) {
          actors.head.ask(MakeBid(auctionId,newBid,_)) (timeout, context.system.scheduler)
        } else {
          Future.failed(new IllegalStateException("AuctionSpawner no disponible"))
        }
      )
    }

    def createAuction(auctionId:String, newAuction: Auction): Future[String] =  {
      getActors(context, AuctionSpawnerActor.AuctionSpawnerServiceKey).flatMap(actors =>
        if (!actors.isEmpty) {
          actors.head.ask(CreateAuction(auctionId,newAuction,_))(timeout, context.system.scheduler)
        } else {
          Future.failed(new IllegalStateException("AuctionSpawner no disponible"))
        }
      )
    }
    def cancelAuction(auctionId: String): Future[String] = Future {
      // TODO integrar con actor
      "Ok!"
    }

//    val routes: Route = pathPrefix("auctions") {
  val routes: Route = pathPrefix("bids") {

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
                complete(StatusCodes.Created, createAuction(auctionId,newAuction))
              }
            },
            put {
              entity(as[Bid]) { newBid =>
                complete(StatusCodes.Accepted, makeBid(auctionId,newBid))
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