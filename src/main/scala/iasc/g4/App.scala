package iasc.g4

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

import scala.io.StdIn
import iasc.g4.actors._
import iasc.g4.routes.Routes
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory

object App{

  /**
   * start http server
   * @param routes
   * @param system
   */
  private def startHttpServer(routes: Route, system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start
    println(s"SERVER STARTS2")
    implicit val classicSystem: akka.actor.ActorSystem = system.classicSystem
    import system.executionContext

    val port = system.settings.config.getInt("my-app.server.port")
    val futureBinding = Http().bindAndHandle(routes, "localhost", port)
    println(s"Server online at http://localhost:$port/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    futureBinding
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
  // definir comportamiento base del sistema (Esto definiría el actor SystemSupervisor)
  object RootBehavior {

    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
      println(s"SERVER STARTS")
      val cluster = Cluster(context.system)
      println(cluster.selfMember)

      if (cluster.selfMember.hasRole("administrator")) {
        println("Administrator")
        val workersPerNode =
          context.system.settings.config.getInt("transformation.workers-per-node")
        (1 to workersPerNode).foreach { n =>
          context.spawn(AuctionActor(), s"Worker$n")
          println("Worker Created:" + n)
        }
      }
      if (cluster.selfMember.hasRole("AuctionSpawner")) {
        val auctionSpawner = context.spawn(AuctionSpawnerActor(), "AuctionSpawner")
        context.watch(auctionSpawner)
        val userSubscriber = context.spawn(BuyersSubscriptorActor(), "UserSubscriberActor")
        context.watch(userSubscriber)
        val routeDefs = new Routes(userSubscriber, auctionSpawner)(context.system)
        startHttpServer(routeDefs.routes(), context.system)
      }

      //val userSubscriber = context.spawn(BuyersSubscriptorActor(), "UserSubscriberActor")
      //val auctionSpawner = context.spawn(AuctionSpawnerActor(), "AuctionSpawnerActor")
      //context.watch(userSubscriber)
      //context.watch(auctionSpawner)
      //val routeDefs = new Routes(userSubscriber, auctionSpawner) (context.system)
      //startHttpServer(routeDefs.routes(), context.system)
      Behaviors.empty
    }
  }

  def main(args: Array[String]): Unit = {
      require(args.length == 2, "Usage: role port")
      startup(args(0), args(1).toInt)
  }

  def startup(role: String, port: Int): Unit = {
    // Override the configuration of the port and role
    println("PORT:" + port)
    val config = ConfigFactory
      .parseString(s"""
        akka.remote.artery.canonical.port=$port
        akka.cluster.roles = [$role]
        """)
      .withFallback(ConfigFactory.load("transformation"))

    ActorSystem[Nothing](RootBehavior(), "TP" , config)
  }
}