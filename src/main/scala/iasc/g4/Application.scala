package iasc.g4

import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import scala.io.StdIn
import iasc.g4.actors._

object Application extends App {

  /**
   * start http server
   * @param routes
   * @param system
   */
  private def startHttpServer(routes: Route, system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start
    implicit val classicSystem: akka.actor.ActorSystem = system.classicSystem
    import system.executionContext

    val port = system.settings.config.getInt("my-app.server.port")
    val futureBinding = Http().bindAndHandle(routes, "localhost", port)
    println(s"Server online at http://localhost:${port}/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    futureBinding
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
  // definir comportamiento base del sistema (Esto definiría el actor SystemSupervisor)
  val rootBehavior = Behaviors.setup[Nothing] { context =>
    val userSubscriber = context.spawn(BuyersSubscriptorActor(), "UserSubscriberActor")
    val auctionSpawner = context.spawn(AuctionSpawnerActor(), "AuctionSpawnerActor")
    context.watch(userSubscriber)
    context.watch(auctionSpawner)
    val routeDefs = new Routes(userSubscriber, auctionSpawner) (context.system)
    startHttpServer(routeDefs.routes(), context.system)
    Behaviors.empty
  }
  val system = ActorSystem[Nothing](rootBehavior, "TP")
}