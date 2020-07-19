package iasc.g4

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.typed.Cluster
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.typesafe.config.ConfigFactory
import iasc.g4.actors._
import iasc.g4.routes.Routes

import scala.io.StdIn

/**
 * Singleton principal del sistema
 */
object App{

  /**
   * Comportamiento base del sistema
   */
  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
      val cluster = Cluster(context.system)
      println(cluster.selfMember)

      if (cluster.selfMember.hasRole("seed")) {
        // empty
      } else if (cluster.selfMember.hasRole("notifier-spawner")) {
        context.spawn(NotifierSpawnerActor(), "NotifierSpawner")

      } else if (cluster.selfMember.hasRole("auction-spawner")) {
        context.spawn(AuctionSpawnerActor(), "AuctionSpawner")

      }else if (cluster.selfMember.hasRole("buyers-subscriptor")) {
        context.spawn(BuyersSubscriptorActor(), "BuyerSubscriptor")

      } else if (cluster.selfMember.hasRole("http-server")) {
        startHttpServer(new Routes(context).routes(), context.system)
      } else {
        throw new IllegalArgumentException(s"Role no encontrado: $cluster.selfMember.roles.head")
      }
      Behaviors.empty
    }
  }

  /**
   * Inicio de la app
   * @param args parámetros de inicio de la app
   */
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

  /**
   * Inicio de una nueva instancia
   * @param role rol que va a tomar la nueva instancia
   * @param port puerto donde estará disponible
   */
  def startup(role: String, port: Int): Unit = {
    println(s"Role: $role escuchando en $port")
    val config = ConfigFactory
      .parseString(s"""
        akka.remote.artery.canonical.port=$port
        akka.cluster.roles = [$role]
        """)
      .withFallback(ConfigFactory.load("transformation"))

    ActorSystem[Nothing](RootBehavior(), "TP" , config)
  }

  /**
   * Iniciar http server
   * @param routes rutas http
   * @param system actor system
   */
  private def startHttpServer(routes: Route, system: ActorSystem[_]): Unit = {
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
}