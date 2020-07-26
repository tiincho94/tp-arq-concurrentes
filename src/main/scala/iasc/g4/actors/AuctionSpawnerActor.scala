package iasc.g4.actors

//import akka.actor.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import iasc.g4.models.AuctionInstance
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, DispatcherSelector, SupervisorStrategy}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import iasc.g4.models.Models.{Auction, Auctions, Bid, Buyer, Command, OperationPerformed}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ddata.DistributedData
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory
import iasc.g4.App.{Roles, RootBehavior, startHttpServer}
import iasc.g4.actors.entities.auctionPoolEntity
import iasc.g4.routes.Routes
import iasc.g4.util.Util

/**
 * Actor spawner de Auctions. Maneja nuevas subastas, su cancelación, etc
 */
object AuctionSpawnerActor {
  //  TODO Revisar Escenario 7 de la consigna -> Debería no ser fijo, primer implementación sería crear
  //  libremente nuevas y en una segunda meter lo que pide el escenario 7

  trait AuctionSpawnerCommand extends Command

  var auctionPool = Set[AuctionInstance]()
  val AuctionSpawnerServiceKey = ServiceKey[AuctionSpawnerCommand]("AuctionSpawner")
  // definición de commands (acciones a realizar)
  final case class GetAuctions(replyTo: ActorRef[Auctions]) extends AuctionSpawnerCommand
  final case class DeleteAuction(auctionId: String, replyTo: ActorRef[String]) extends AuctionSpawnerCommand
  // final case class CreateAuction(newAuction: Auction, replyTo: ActorRef[OperationPerformed]) extends AuctionSpawnerCommand
  final case class CreateAuction(auctionId:String,newAuction: Auction, replyTo: ActorRef[String]) extends AuctionSpawnerCommand
  final case class MakeBid(auctionId:String, newBid:Bid, replyTo: ActorRef[String]) extends AuctionSpawnerCommand
  final case class FreeAuction(id:String) extends AuctionSpawnerCommand
  sealed trait Event
  private case object Tick extends Event
  //private final case class WorkersUpdated(newWorkers: Set[ActorRef[Worker.TransformText]]) extends Event
  private final case class TransformCompleted(originalText: String, transformedText: String) extends Event
  private final case class JobFailed(why: String, text: String) extends Event

  // instanciación del objeto
  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    var poolSize = ctx.system.settings.config.getInt("akka.cluster.auction-pool-size")

    ctx.log.info("Configurando AuctionSpawner")
    ctx.system.receptionist ! Receptionist.Register(AuctionSpawnerServiceKey, ctx.self)

    (0 to poolSize).foreach { n =>
      //val behavior = AuctionActor(n,ctx.self)
      var auctionActorServiceKey = ServiceKey[Command](s"AuctionActor$n")
      startAuction(n,auctionActorServiceKey)
      this.auctionPool += auctionPoolEntity.getAuctionInstance(n, auctionActorServiceKey)

      /*
      val behavior = AuctionActor(n,auctionActorServiceKey)
      //val behavior = AuctionActor()
      println(s"Spawning auction $n...")
      val ref : ActorRef[Command] = ctx.spawn(behavior, s"Auction$n")
      println(s"Auction $n ok: $ref")
      Behaviors.supervise(behavior).onFailure[Exception](SupervisorStrategy.restart)
      this.auctionPool += auctionPoolEntity.getAuctionInstance(n, ref)*/
    }

    auctionPoolEntity.set(this.auctionPool)

    Behaviors.receiveMessage {
      case GetAuctions(replyTo) =>
        //replyTo ! Auctions(auctions)
        Behaviors.same
      case DeleteAuction(auctionId, replyTo) =>
        var auctionInstance = auctionPoolEntity.getAuctionById(auctionId)
        if(auctionInstance!=None) {
           //auctionInstance.head.getAuction() ! AuctionActor.DeleteAuction(replyTo)
          Util.getOneActor(ctx,auctionInstance.head.auctionActorServiceKey) ! AuctionActor.DeleteAuction(replyTo)
        }
        Behaviors.same
      case CreateAuction(auctionId,newAuction, replyTo) =>
        var auctionInstance = auctionPoolEntity.getFreeAuction(auctionId,replyTo)
        if(auctionInstance!=null)
          //auctionActor ! AuctionActor.StartAuction(auctionId,newAuction,replyTo)
        Util.getOneActor(ctx,auctionInstance.auctionActorServiceKey) ! AuctionActor.StartAuction(auctionId,newAuction,replyTo)
        Behaviors.same
      case MakeBid(auctionId,newBid,replyTo) =>
        var auctionInstance = auctionPoolEntity.getAuctionById(auctionId)
        if(auctionInstance != None) {
          //auctionActor.head.getAuction() ! AuctionActor.MakeBid(newBid,replyTo)
          Util.getOneActor(ctx,auctionInstance.head.auctionActorServiceKey) ! AuctionActor.MakeBid(newBid,replyTo)
        } else {
          replyTo ! s"Subasta no encontrada: ${newBid.auctionId}"
        }
        Behaviors.same
      case FreeAuction(id) =>
        auctionPoolEntity.freeAuction(id)
        Behaviors.same
    }
  }

  def startAuction(index: Long,auctionActorServiceKey : ServiceKey[Command]): Unit = {

    val config = ConfigFactory
      .parseString(s"""
        akka.remote.artery.canonical.port=0
        akka.cluster.roles = [${Roles.Auction.roleName}]
        """)
      .withFallback(ConfigFactory.load("transformation"))

    val system = ActorSystem[Nothing](RootAuctionBehavior(index,auctionActorServiceKey), "TP" , config)
    system.log.info(s"${Roles.Auction.roleName} (Role) available at ${system.address}")

  }

}

object RootAuctionBehavior {
  import akka.actor.ActorRef
  def apply(index: Long,auctionActorServiceKey : ServiceKey[Command]) : Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    val cluster = Cluster(context.system)
    val replicator: ActorRef = DistributedData(context.system).replicator
    context.spawn(AuctionActor(index,auctionActorServiceKey), s"Auction$index")
    Behaviors.empty
  }
}

