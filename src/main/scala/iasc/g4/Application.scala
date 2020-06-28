package iasc.g4

import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import scala.io.StdIn
import iasc.g4.actors._

object Application extends App {

  private def startHttpServer(routes: Route, system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start
    implicit val classicSystem: akka.actor.ActorSystem = system.classicSystem
    import system.executionContext
    val futureBinding = Http().bindAndHandle(routes, "localhost", 8080)
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    futureBinding
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

  val rootBehavior = Behaviors.setup[Nothing] { context =>
    val userSubscriber = context.spawn(BuyersSubscriptorActor(), "UserSubscriberActor")
    val auctionSpawner = context.spawn(AuctionSpawnerActor(), "AuctionSpawnerActor")
    //context.watch(userSubscriber)
    //context.watch(auctionSpawner)
    val routeDefs = new Routes(userSubscriber, auctionSpawner) (context.system)
    startHttpServer(routeDefs.routes(), context.system)
    Behaviors.empty
  }
  val system = ActorSystem[Nothing](rootBehavior, "TP")
}