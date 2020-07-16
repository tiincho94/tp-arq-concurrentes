package iasc.g4

import akka.NotUsed
import akka.actor.IllegalActorStateException
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.MemberStatus
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

import scala.io.StdIn
import iasc.g4.actors._
import iasc.g4.routes.Routes
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory
import akka.actor.typed.scaladsl.adapter._

import scala.concurrent.duration._

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

    val port = system.settings.config.getInt("akka.server.port")
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
      val cluster = Cluster(context.system)
      println(cluster.selfMember)

      if (cluster.selfMember.hasRole("seed")) {
        val workersPerNode =
          context.system.settings.config.getInt("transformation.workers-per-node")
        (1 to workersPerNode).foreach { n =>
          context.spawn(Worker(), s"Worker$n")
        }
      } else if (cluster.selfMember.hasRole("notifier-spawner")) {
        context.spawn(NotifierSpawnerActor(), "NotifierSpanwerActor")

      } else if (cluster.selfMember.hasRole("auction-spawner")) {
        context.spawn(AuctionSpawnerActor(), "AuctionSpawner")

      }else if (cluster.selfMember.hasRole("buyers-subscriptor")) {
        context.spawn(BuyersSubscriptorActor(), "BuyerSubscriptor")

      } else if (cluster.selfMember.hasRole("http-server")) {
        context.system.scheduler.scheduleAtFixedRate(0.seconds, 2.seconds)(()=> {
          println("Miembros cluster:" + cluster.state.getMembers)
        })(context.executionContext)
        startHttpServer(new Routes(context).routes(), context.system)
      } else {
        throw new IllegalArgumentException(s"Role no encontrado: $cluster.selfMember.roles.head")
      }
      Behaviors.empty
    }
    }


  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      startup("seed", 25251)
      Thread.sleep(2000)
      startup("seed", 25252)
      Thread.sleep(2000)
      startup("auction-spawner", 0)
      startup("buyers-subscriptor", 0)
      startup("notifier-spawner", 0)
      Thread.sleep(3000)
      startup("http-server", 0)
    } else {
      require(args.length == 2, "Usage: role port")
      startup(args(0), args(1).toInt)
    }
  }

  def startup(role: String, port: Int): Unit = {
    // Override the configuration of the port and role
    println(s"Role: $role escuchando en $port")
    val config = ConfigFactory
      .parseString(s"""
        akka.remote.artery.canonical.port=$port
        akka.cluster.roles = [$role]
        """)
      .withFallback(ConfigFactory.load("transformation"))

    ActorSystem[Nothing](RootBehavior(), "TP" , config)
  }
}