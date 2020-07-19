package iasc.g4.actors

import iasc.g4.models.AuctionInstance
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, SupervisorStrategy}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import iasc.g4.models.Models.{Auction, Auctions, Command, OperationPerformed}
import akka.actor.typed.scaladsl.Routers
import akka.actor.typed.scaladsl.Behaviors
import akka.routing.Router
import cats.syntax.group
import iasc.g4.actors.AuctionSpawnerActor.auctionPool
import iasc.g4.actors.entities.auctionPoolEntity

/**
 * Actor spawner de Auctions. Maneja nuevas subastas, su cancelación, etc
 */
object AuctionSpawnerActor {
  // TODO Revisar Escenario 7 de la consigna -> Debería no ser fijo, primer implementación sería crear
  //  libremente nuevas y en una segunda meter lo que pide el escenario 7

  trait AuctionSpawnerCommand extends Command

  var auctionPool = Set[AuctionInstance]()
  val AuctionSpawnerServiceKey = ServiceKey[AuctionSpawnerCommand]("AuctionSpawner")
  // definición de commands (acciones a realizar)
  final case class GetAuctions(replyTo: ActorRef[Auctions]) extends AuctionSpawnerCommand
  final case class DeleteAuction(auctionId: String, replyTo: ActorRef[OperationPerformed]) extends AuctionSpawnerCommand
  // final case class CreateAuction(newAuction: Auction, replyTo: ActorRef[OperationPerformed]) extends AuctionSpawnerCommand
  final case class CreateAuction(auctionId:String,newAuction: Auction, replyTo: ActorRef[String]) extends AuctionSpawnerCommand
  final case class FreeAuction(id:String) extends AuctionSpawnerCommand
  sealed trait Event
  private case object Tick extends Event
  private final case class WorkersUpdated(newWorkers: Set[ActorRef[Worker.TransformText]]) extends Event
  private final case class TransformCompleted(originalText: String, transformedText: String) extends Event
  private final case class JobFailed(why: String, text: String) extends Event

  // instanciación del objeto
  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.info("Configurando AuctionSpawner")
    ctx.system.receptionist ! Receptionist.Register(AuctionSpawnerServiceKey, ctx.self)

    // val group = Routers.group(AuctionActor.AuctionActorServiceKey).withConsistentHashingRouting(1,{(arg)=>1.toString})
    // val router2 = ctx.spawn(group, "worker-group");

    (0 to 1).foreach { n =>
      val behavior = AuctionActor()
      println(s"Spawning auction $n...")
      val ref : ActorRef[Command] = ctx.spawn(behavior, s"Auction$n")
      println(s"Auction $n ok: $ref")
      Behaviors.supervise(behavior).onFailure[Exception](SupervisorStrategy.resume)
      ref ! AuctionActor.Init(n, ctx.self)
      this.auctionPool += auctionPoolEntity.getAuctionInstance(n, ref)
    }

    auctionPoolEntity.set(this.auctionPool)

    Behaviors.receiveMessage {
      case GetAuctions(replyTo) =>
        //replyTo ! Auctions(auctions)
        Behaviors.same
      case DeleteAuction(auctionId, replyTo) =>
        // TODO implementar
        replyTo ! OperationPerformed("TBD")
        Behaviors.same
      case CreateAuction(auctionId,newAuction, replyTo) =>
        var auctionActor = auctionPoolEntity.getFreeAuctionActor(auctionId,replyTo)
        if(auctionActor!=null)
          auctionActor ! AuctionActor.StartAuction(auctionId,newAuction,replyTo)
        Behaviors.same
      case FreeAuction(id) =>
        auctionPoolEntity.freeAuction(id)
        Behaviors.same
    }
  }


}