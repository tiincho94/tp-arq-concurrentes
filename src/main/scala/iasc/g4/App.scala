package iasc.g4

import akka.actor.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.ddata.DistributedData
import akka.cluster.typed.Cluster
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.typesafe.config.ConfigFactory
import iasc.g4.actors._
import iasc.g4.routes.Routes

/**
 * Singleton principal del sistema
 */
object App{

  /**
   * Roles posibles para instancias del cluster
   */
  object Roles extends Enumeration {
    protected case class Val(roleName: String) extends super.Val
    val Seed: Val = Val("seed")
    val Listener: Val = Val("listener")
    val HTTPServer: Val = Val("http-server")
    val BuyersSuscriptor: Val = Val("buyers-suscriptor")
    val AuctionSpawner: Val = Val("auction-spawner")
    val NotifierSpawner: Val = Val("notifier-spawner")
    val Auction: Val = Val("auction")
  }

  /**
   * Inicio de la app
   * @param args parámetros de inicio de la app
   */
  def main(args: Array[String]): Unit = {
    if (args.length == 1) {
      startup(Roles.Seed.roleName, 25251)
      Thread.sleep(2000)
      startup(Roles.Seed.roleName, 25252)
      Thread.sleep(2000)
      startup(Roles.BuyersSuscriptor.roleName, 0)
      startup(Roles.NotifierSpawner.roleName, 0)
      startup(Roles.HTTPServer.roleName, 0)
    } else if (args.isEmpty) {
      startup(Roles.Seed.roleName, 25251)
      Thread.sleep(2000)
      startup(Roles.Seed.roleName, 25252)
      Thread.sleep(2000)
      startup(Roles.AuctionSpawner.roleName, 0)
      startup(Roles.BuyersSuscriptor.roleName, 0)
      startup(Roles.NotifierSpawner.roleName, 0)
      Thread.sleep(4000)
      startup(Roles.HTTPServer.roleName, 0)
    } else {
      require(args.length == 2, s"Usage: role port")
      var auctionIndex = 0l
      if (args.length >= 3) {
        auctionIndex = args(2).toLong
      }
      startup(args(0), args(1).toInt, auctionIndex)
    }
  }

  /**
   * Inicio de una nueva instancia
   * @param role rol que va a tomar la nueva instancia
   * @param port puerto donde estará disponible
   * @param auctionIndex index de auction. sólo para auction actor
   */
  def startup(role: String, port: Int, auctionIndex: Long = 0): Unit = {

    val config = ConfigFactory
      .parseString(s"""
        akka.remote.artery.canonical.port=$port
        akka.cluster.roles = [$role]
        """)
      .withFallback(ConfigFactory.load("transformation"))

    val system = ActorSystem[Nothing](RootBehavior(auctionIndex), "TP" , config)
    system.log.info(s"$role (Role) available at ${system.address}")

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
    Http().bindAndHandle(routes, "localhost", port)
    system.log.info(s"HTTP Server online at http://localhost:$port/")
  }

  /**
   * Comportamiento base del sistema
   */
  object RootBehavior {
    def apply(auctionIndex: Long = 0): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
      val cluster = Cluster(context.system)
      val replicator: ActorRef = DistributedData(context.system).replicator
      if (cluster.selfMember.hasRole(Roles.Seed.roleName)) {
        // empty
      } else if (cluster.selfMember.hasRole(Roles.Listener.roleName)) {
        context.spawn(SimpleClusterListener(), "SimpleClusterListener")

      } else if (cluster.selfMember.hasRole(Roles.NotifierSpawner.roleName)) {
        context.spawn(NotifierSpawnerActor(), "NotifierSpawner")

      } else if (cluster.selfMember.hasRole(Roles.AuctionSpawner.roleName)) {
        context.spawn(AuctionSpawnerActor(), "AuctionSpawner")

      }else if (cluster.selfMember.hasRole(Roles.BuyersSuscriptor.roleName)) {
        context.spawn(BuyersSubscriptorActor(), "BuyerSubscriptor")

      } else if (cluster.selfMember.hasRole(Roles.HTTPServer.roleName)) {
        startHttpServer(new Routes(context).routes(), context.system)

      } else if (cluster.selfMember.hasRole(Roles.Auction.roleName)) {
        context.spawn(AuctionActor(auctionIndex, AuctionSpawnerActor.generateAuctionServiceKey(auctionIndex)), s"Auction$auctionIndex")

      } else {
        throw new IllegalArgumentException(s"Role no encontrado: $cluster.selfMember.roles.head")
      }
      Behaviors.empty
    }
  }
}