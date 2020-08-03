package iasc.g4.actors

import java.io.File

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.typed.scaladsl.Replicator.{Get, GetSuccess, Update}
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata._
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory
import iasc.g4.App.Roles
import iasc.g4.models.Models.{Auction, AuctionInstance, Auctions, Bid, Command, InternalCommand}

import scala.concurrent.duration._
import scala.sys.process.Process
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
  final case class NewAuctionCreated(index: Long, auctionId: String, newAuction: Auction, createNode: Boolean, replyTo: ActorRef[String]) extends AuctionSpawnerCommand

  //Mensajes internos
  final case class InternalDeleteAuction(auctionId: String, replyTo: ActorRef[String]) extends AuctionSpawnerCommand
  private case class InternalDeleteAuctionResponse(auctionId:String, replyTo: ActorRef[String], rsp: GetResponse[LWWMap[Long, AuctionInstance]]) extends InternalCommand
  private case class InternalInitAuctionNodesResponse(rsp: GetResponse[LWWMap[Long, AuctionInstance]]) extends InternalCommand
  private case class InternalCreateAuctionResponse[A <: ReplicatedData](rsp: UpdateResponse[A]) extends InternalCommand
  private case class InternalMakeBidResponse(auctionId:String, newBid: Bid,replyTo: ActorRef[String], rsp: GetResponse[LWWMap[Long, AuctionInstance]]) extends InternalCommand
  private case class InternalFreeAuctionResponse[A <: ReplicatedData](rsp: UpdateResponse[A]) extends InternalCommand

  private val timeout = 3.seconds
  private val readMajority = ReadMajority(timeout)
  private val writeMajority = WriteMajority(timeout)

  // instanciación del objeto
  def apply(): Behavior[Command] = Behaviors.setup { auctionSpawnerContext =>
      DistributedData.withReplicatorMessageAdapter[Command, LWWMap[Long, AuctionInstance]] { replicator =>

        implicit val node: SelfUniqueAddress = DistributedData(auctionSpawnerContext.system).selfUniqueAddress
        val DataKey = LWWMapKey[Long, AuctionInstance]("auctionPool")

        val poolSize = auctionSpawnerContext.system.settings.config.getInt("akka.cluster.auction-pool-size")

        auctionSpawnerContext.log.info("Configurando AuctionSpawner")
        auctionSpawnerContext.system.receptionist ! Receptionist.Register(AuctionSpawnerServiceKey, auctionSpawnerContext.self)

        initAuctionNodes()

        def initAuctionNodes() = {
          replicator.askGet(
            askReplyTo => Get(DataKey, readMajority, askReplyTo),
            rsp => InternalInitAuctionNodesResponse(rsp)
          )
        }

        def freeAuction(auctionId: String,data: LWWMap[Long, AuctionInstance]): LWWMap[Long, AuctionInstance] = {
          data.entries.values.find(auctionInstance => auctionInstance.auctionId==auctionId) match {
            case Some(someAuctionInstance) => {
              var newAuctionInstance = new AuctionInstance(someAuctionInstance.index,"",true)
              val a = data.remove(node,newAuctionInstance.index)
              a :+ (newAuctionInstance.index -> newAuctionInstance)
            }
            case _ => data
          }
        }

        def startAuctionNode(index: Long): Unit = {
          val config = ConfigFactory
            .parseString(
              s"""
              akka.remote.artery.canonical.port=0
              akka.cluster.roles = [${Roles.Auction.roleName}]
              """)
            .withFallback(ConfigFactory.load("transformation"))
          val system = ActorSystem[Nothing](RootAuctionBehavior(index), "TP", config)
          system.log.info(s"${Roles.Auction.roleName} (Role) available at ${system.address}")
        }

//        def startAuctionNode(index: Long) = {
//          Process(s"sbt.bat 'runMain iasc.g4.App auction 0 $index'", new File("C:\\Users\\Dell\\Documents\\Facultad\\concurr\\tp-arq-concurrentes")).run()
//        }

        def createAuction(data: LWWMap[Long, AuctionInstance], auctionId: String, newAuction:Auction ,replyTo: ActorRef[String], onCreateRef: ActorRef[Command]): LWWMap[Long, AuctionInstance] = {
          if (data.size < poolSize) {
            //Instancio un nodo, creo un actor y le asigno la nueva subasta
            var index : Long = data.size+1
            onCreateRef ! NewAuctionCreated(index, auctionId, newAuction, true, replyTo)
            var auctionInstance = new AuctionInstance(index,auctionId,false)
            data :+ (index -> auctionInstance)
          } else {
            //Busco una instancia libre y le asigno la subasta
            //Si no hay subastas libres, tiro
            data.entries.values.find(auctionInstance => auctionInstance.isFree) match {
              case Some(someAuctionInstance) => {
                onCreateRef ! NewAuctionCreated(someAuctionInstance.index, auctionId, newAuction, false, replyTo)
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

        /**
         * Envia un mensaje a un AuctionActor. Intenta levantarlo si está caído
         * @param auctionId
         * @param index
         * @param command
         * @param replyTo
         * @param errorMessage
         */
        def sendAuctionMessage(auctionId: String, index: Long, command: Command, replyTo: ActorRef[String], errorMessage: String) = {
          Util.getOneActor(auctionSpawnerContext, generateAuctionServiceKey(index)) match {
            case Some(actor) => actor ! command
            case None => {
              println("No se encotró el auction actor... se intentará levantar una nueva instancia")
              startAuctionNode(index)
              Util.getOneActor(auctionSpawnerContext, generateAuctionServiceKey(index), 3) match {
                case Some(newNode) => {
                  newNode ! AuctionActor.ResumeAuction(auctionId)
                  newNode ! command
                }
                case None => {
                  if (null != replyTo) {
                    replyTo ! errorMessage
                  }
                }
              }
            }
          }
        }

        Behaviors.receiveMessage {
          case GetAuctions(replyTo) =>
            replyTo ! Auctions(Set[Auction]())
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
                auctionInstance.auctionId==auctionId) match {
                  case Some(someAuctionInstance) => {
                    sendAuctionMessage(someAuctionInstance.auctionId, someAuctionInstance.index,
                      AuctionActor.CancelAuction(replyTo), replyTo, s"Error intentando cancelar Auction $auctionId. No se pudo obtener ref al Auction Actor :(")
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
                  createAuction(auctionPool, auctionId,newAuction, replyTo, auctionSpawnerContext.self)
                }
              },
              InternalCreateAuctionResponse.apply)
            Behaviors.same
          case InternalCreateAuctionResponse(_: UpdateSuccess[_]) => Behaviors.same
          case InternalCreateAuctionResponse(_: UpdateTimeout[_]) => Behaviors.same
          case InternalCreateAuctionResponse(e: UpdateFailure[_]) => throw new IllegalStateException("Unexpected failure: " + e)

          case NewAuctionCreated(index, auctionId, newAuction, createNode, replyTo) => {
            if (createNode) {
              startAuctionNode(index)
            }
            Util.getOneActor(auctionSpawnerContext, generateAuctionServiceKey(index), 3) match {
              case Some(actor) => actor ! AuctionActor.StartAuction(auctionId, newAuction, replyTo)
              case None => replyTo ! "No se pudo obtener referencia al AuctionActor creado :("
            }
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
                  auctionInstance.auctionId==auctionId) match {
                  case Some(someAuctionInstance) => {
                    sendAuctionMessage(someAuctionInstance.auctionId, someAuctionInstance.index,
                      AuctionActor.MakeBid(newBid, replyTo), replyTo, s"Error intentando realizar una oferta en subasta: $auctionId. No se pudo obtener ref al Auction Actor :(")
                  }
                  case None => replyTo ! s"Error intentando realizar una oferta en subasta: $auctionId. Auction no encontrado"
                }
              }
              case _ => replyTo ! s"Error intentando realizar una oferta en subasta: $auctionId. Error de sistema"
            }
            Behaviors.same
          case FreeAuction(auctionId) =>
            //auctionPoolEntity.freeAuction(id)
            println("AuctionSpawnerActor Free Auction...")
            replicator.askUpdate(
              askReplyTo => Update(DataKey, LWWMap.empty[Long, AuctionInstance], writeMajority, askReplyTo) {
                auctionPool => freeAuction(auctionId,auctionPool)
              },
              InternalFreeAuctionResponse.apply)
            Behaviors.same
          case InternalFreeAuctionResponse(_: UpdateSuccess[_]) => Behaviors.same
          case InternalFreeAuctionResponse(_: UpdateTimeout[_]) => Behaviors.same
          case InternalFreeAuctionResponse(e: UpdateFailure[_]) => throw new IllegalStateException("Unexpected failure: " + e)
          case InternalInitAuctionNodesResponse(g @ GetSuccess(DataKey)) =>
            val data = g.get(DataKey)
            data.entries.values.foreach( ai => {
              startAuctionNode(ai.index)
              if (!ai.isFree) {
                Util.getOneActor(auctionSpawnerContext, generateAuctionServiceKey(ai.index), 3) match {
                  case Some(actor) => actor ! AuctionActor.ResumeAuction(ai.auctionId)
                  case None => {
                    auctionSpawnerContext.log.error(s"No fue posible obtener ref al auction actor ${ai.index}")
                  }
                }
              }
            })
            Behaviors.same
          case InternalInitAuctionNodesResponse(NotFound(DataKey, _)) => Behaviors.same
          case InternalInitAuctionNodesResponse(GetFailure(DataKey, _)) => Behaviors.same
        }
      }
    }
}

object RootAuctionBehavior {
  def apply(index: Long): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    Cluster(context.system)
    DistributedData(context.system).replicator
    context.spawn(AuctionActor(index, AuctionSpawnerActor.generateAuctionServiceKey(index)), s"Auction$index")
    Behaviors.empty
  }
}

