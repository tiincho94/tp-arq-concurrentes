package iasc.g4.actors

import scala.concurrent.duration._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, DispatcherSelector, SupervisorStrategy}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import iasc.g4.models.Models.{Auction, AuctionInstance, Auctions, Bid, Buyer, Command, InternalCommand, OperationPerformed}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ddata.Replicator.{GetResponse, ReadMajority, UpdateFailure, UpdateResponse, UpdateSuccess, UpdateTimeout, WriteMajority}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.cluster.ddata.typed.scaladsl.Replicator.{Get, GetSuccess, Update}
import akka.cluster.ddata.{Flag, LWWMap, LWWMapKey, ReplicatedData, SelfUniqueAddress}
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory
import iasc.g4.App.{Roles, RootBehavior, startHttpServer}
import iasc.g4.actors.BuyersSubscriptorActor.{InternalBuyerGetResponse, InternalUpdateResponse, readMajority, writeMajority}
//import iasc.g4.actors.entities.auctionPoolEntity
import iasc.g4.util.Util

/**
 * Actor spawner de Auctions. Maneja nuevas subastas, su cancelación, etc
 */
object AuctionSpawnerActor {

  trait AuctionSpawnerCommand extends Command
  val AuctionSpawnerServiceKey = ServiceKey[AuctionSpawnerCommand]("AuctionSpawner")

  /**
   * Genera una ServiceKey para identificar actores del tipo AuctionActor en base a un index
   * @param index
   * @return
   */
  def generateAuctionServiceKey(index: Long): ServiceKey[Command] = {
    ServiceKey[Command](s"AuctionActor$index")
  }

  //Mensajes externos
  final case class GetAuctions(replyTo: ActorRef[Auctions]) extends AuctionSpawnerCommand
  final case class DeleteAuction(auctionId: String, replyTo: ActorRef[String]) extends AuctionSpawnerCommand
  final case class CreateAuction(auctionId: String, newAuction: Auction, replyTo: ActorRef[String]) extends AuctionSpawnerCommand
  final case class MakeBid(auctionId: String, newBid: Bid, replyTo: ActorRef[String]) extends AuctionSpawnerCommand
  final case class FreeAuction(id: String) extends AuctionSpawnerCommand
  final case class NewAuctionCreated(index: Long, auctionId: String, newAuction: Auction, replyTo: ActorRef[String]) extends AuctionSpawnerCommand

  //Mensajes internos
  final case class InternalDeleteAuction(auctionId: String, replyTo: ActorRef[String]) extends AuctionSpawnerCommand
  private case class InternalDeleteAuctionResponse(auctionId:String, replyTo: ActorRef[String], rsp: GetResponse[LWWMap[Long, AuctionInstance]]) extends InternalCommand
  private case class InternalCreateAuctionResponse[A <: ReplicatedData](rsp: UpdateResponse[A]) extends InternalCommand
  private case class InternalMakeBidResponse(auctionId:String, newBid: Bid,replyTo: ActorRef[String], rsp: GetResponse[LWWMap[Long, AuctionInstance]]) extends InternalCommand
  private case class InternalFreeAuctionResponse[A <: ReplicatedData](rsp: UpdateResponse[A]) extends InternalCommand

  private val timeout = 3.seconds
  private val readMajority = ReadMajority(timeout)
  private val writeMajority = WriteMajority(timeout)


  // instanciación del objeto
  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    DistributedData.withReplicatorMessageAdapter[Command, Flag] { replicatorFlag =>
      DistributedData.withReplicatorMessageAdapter[Command, LWWMap[Long, AuctionInstance]] { replicator =>

        implicit val node: SelfUniqueAddress = DistributedData(ctx.system).selfUniqueAddress
        val DataKey = LWWMapKey[Long, AuctionInstance]("auctionPool")

        var poolSize = ctx.system.settings.config.getInt("akka.cluster.auction-pool-size")

        ctx.log.info("Configurando AuctionSpawner")
        ctx.system.receptionist ! Receptionist.Register(AuctionSpawnerServiceKey, ctx.self)

        def freeAuction(auctionId: String,data: LWWMap[Long, AuctionInstance]): LWWMap[Long, AuctionInstance] = {
          data.entries.values.find(auctionInstance => auctionInstance.id==auctionId) match {
            case Some(someAuctionInstance) => {
              var newAuctionInstance = new AuctionInstance(someAuctionInstance.index,"",true)
              //data.put(node,newAuctionInstance.index,newAuctionInstance)
              val a = data.remove(node,newAuctionInstance.index)
              a :+ (newAuctionInstance.index -> newAuctionInstance)
            }
            case _ => data
          }
        }

        def startAuctionNode(index: Long, auctionId: String, newAuction:Auction, replyTo: ActorRef[String]): Unit = {
          val config = ConfigFactory
            .parseString(
              s"""
              akka.remote.artery.canonical.port=0
              akka.cluster.roles = [${Roles.Auction.roleName}]
              """)
            .withFallback(ConfigFactory.load("transformation"))
          val system = ActorSystem[Nothing](RootAuctionBehavior(index), "TP", config)
          system.log.info(s"${Roles.Auction.roleName} (Role) available at ${system.address}")
          Util.getOneActor(ctx, generateAuctionServiceKey(index), 3) match {
            case Some(actor) => actor ! AuctionActor.StartAuction(auctionId, newAuction, replyTo)
            case None => replyTo ! "No se pudo obtener referencia al AuctionActor creado :("
          }
        }

        def createAuction(data: LWWMap[Long, AuctionInstance], auctionId: String, newAuction:Auction ,replyTo: ActorRef[String], onCreateRef: ActorRef[Command]): LWWMap[Long, AuctionInstance] = {
          if (data.size < poolSize) {
            //Instancio un nodo, creo un actor y le asigno la nueva subasta
            var index : Long = data.size+1
            onCreateRef ! NewAuctionCreated(index, auctionId, newAuction, replyTo)
            var auctionInstance = new AuctionInstance(index,auctionId,false)
            data :+ (index -> auctionInstance)
          } else {
            //Busco una instancia libre y le asigno la subasta
            //Si no hay subastas libres, tiro
            data.entries.values.find(auctionInstance => auctionInstance.isFree) match {
              case Some(someAuctionInstance) => {
                Util.getOneActor(ctx, generateAuctionServiceKey(someAuctionInstance.index)) match {
                  case Some(actor) => actor ! AuctionActor.StartAuction(auctionId, newAuction, replyTo)
                  case None => replyTo ! "No se pudo obtener referencia a la instancia asignada :("
                }
                val a = data.remove(node,someAuctionInstance.index)
                a :+ (someAuctionInstance.index -> AuctionInstance(someAuctionInstance.index, auctionId, false))
              }
              case None => {
                replyTo ! "No hay instancias libres"
                data
              }
            }
          }
        }

        Behaviors.receiveMessage {
          case GetAuctions(replyTo) =>
            Behaviors.same
          case DeleteAuction(auctionId, replyTo) =>
            println("Cancel Auction...")
            replicator.askGet(
              askReplyTo => Get(DataKey, readMajority, askReplyTo),
              rsp => InternalDeleteAuctionResponse(auctionId, replyTo, rsp)
            )
            Behaviors.same
          case InternalDeleteAuctionResponse(auctionId, replyTo, rsp) =>
            rsp match {
              case g @ GetSuccess(DataKey) => {
                g.get(DataKey).entries.values.toSet.find(auctionInstance =>
                auctionInstance.id==auctionId) match {
                  case Some(someAuctionInstance) => {
                    Util.getOneActor(ctx, generateAuctionServiceKey(someAuctionInstance.index)) match {
                      case Some(actor) => actor ! AuctionActor.DeleteAuction(replyTo)
                      case None => replyTo ! s"Error intentando cancelar Auction $auctionId. No se pudo obtener ref al Auction Actor :("
                    }
                  }
                  case None => replyTo ! s"Error intentando cancelar Auction $auctionId. Auction no encontrado"
                }
              }
              case _ => replyTo ! s"Error intentando cancelar Auction $auctionId. Error de sistema"
            }
            Behaviors.same
          case CreateAuction(auctionId, newAuction, replyTo) =>
            println("Create Auction...")
            replicator.askUpdate(
              askReplyTo => Update(DataKey, LWWMap.empty[Long, AuctionInstance], writeMajority, askReplyTo) {
                auctionPool => {
                  createAuction(auctionPool, auctionId,newAuction, replyTo, ctx.self)
                }
              },
              InternalCreateAuctionResponse.apply)
            Behaviors.same
          case InternalCreateAuctionResponse(_: UpdateSuccess[_]) => Behaviors.same
          case InternalCreateAuctionResponse(_: UpdateTimeout[_]) => Behaviors.same
          case InternalCreateAuctionResponse(e: UpdateFailure[_]) => throw new IllegalStateException("Unexpected failure: " + e)
          case NewAuctionCreated(index, auctionId, newAuction, replyTo) => {
            startAuctionNode(index, auctionId, newAuction, replyTo)
            Behaviors.same
          }
          case MakeBid(auctionId, newBid, replyTo) =>
            println("Make Bid...")
            replicator.askGet(
              askReplyTo => Get(DataKey, readMajority, askReplyTo),
              rsp => InternalMakeBidResponse(auctionId, newBid,replyTo, rsp)
            )
            Behaviors.same
          case InternalMakeBidResponse(auctionId, newBid,replyTo, rsp) =>
            rsp match {
              case g @ GetSuccess(DataKey) => {
                g.get(DataKey).entries.values.toSet.find(auctionInstance =>
                  auctionInstance.id==auctionId) match {
                  case Some(someAuctionInstance) => {
                    Util.getOneActor(ctx, generateAuctionServiceKey(someAuctionInstance.index)) match {
                      case Some(actor) => actor ! AuctionActor.MakeBid(newBid, replyTo)
                      case None => replyTo ! s"Error intentando realizar una oferta en subasta: $auctionId. No se pudo obtener ref al Auction Actor :("
                    }
                  }
                  case None => replyTo ! s"Error intentando realizar una oferta en subasta: $auctionId. Auction no encontrado"
                }
              }
              case _ => replyTo ! s"Error intentando realizar una oferta en subasta: $auctionId. Error de sistema"
            }
            Behaviors.same
          case FreeAuction(auctionId) =>
            //auctionPoolEntity.freeAuction(id)
            println("Free Auction...")
            replicator.askUpdate(
              askReplyTo => Update(DataKey, LWWMap.empty[Long, AuctionInstance], writeMajority, askReplyTo) {
                auctionPool => freeAuction(auctionId,auctionPool)
              },
              InternalFreeAuctionResponse.apply)
            Behaviors.same
          case InternalFreeAuctionResponse(_: UpdateResponse[_]) => Behaviors.same
          case InternalFreeAuctionResponse(_: UpdateTimeout[_]) => Behaviors.same
          case InternalFreeAuctionResponse(e: UpdateFailure[_]) => throw new IllegalStateException("Unexpected failure: " + e)

        }


        //
      }
    }
  }
}

object RootAuctionBehavior {
  def apply(index: Long): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    val cluster = Cluster(context.system)
    val replicator: ActorRef[Replicator.Command] = DistributedData(context.system).replicator
    context.spawn(AuctionActor(index, AuctionSpawnerActor.generateAuctionServiceKey(index)), s"Auction$index")
    Behaviors.empty
  }
}

