package iasc.g4.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, Behaviors}
import akka.cluster.ddata.ReplicatedData
import akka.cluster.ddata.Replicator._
import akka.http.scaladsl.model.DateTime
import iasc.g4.actors.AuctionActor._
import iasc.g4.actors.AuctionSpawnerActor.FreeAuction
import iasc.g4.models.Models.{Auction, AuctionActorState, Bid, Command, InternalCommand}
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.ActorContext
import akka.cluster.ddata.Replicator.{ReadMajority, WriteMajority}
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.{Get, GetSuccess, Update}
import akka.cluster.ddata.{LWWMap, LWWMapKey, SelfUniqueAddress}
import iasc.g4.actors.NotifierSpawnerActor._
import iasc.g4.util.Util._

import scala.concurrent.duration._


/**
 * Actor que maneja una subasta
 */
object AuctionActor {
  //Mensajes Externos
  final case class StartAuction(auctionId:String, newAuction: Auction, replyTo: ActorRef[String]) extends Command
  final case class MakeBid(newBid:Bid, replyTo: ActorRef[String]) extends Command
  final case class Init(index:Long , auctionSpawner : ActorRef[AuctionSpawnerActor.AuctionSpawnerCommand]) extends Command
  final case class EndAuction(auctionId : String) extends Command
  final case class CancelAuction(replyTo:ActorRef[String]) extends Command
  final case class ResumeAuction(auctionId: String) extends Command

  //Mensajes Internos
  private case class InternalStartAuctionResponse[A <: ReplicatedData](rsp: UpdateResponse[A],newAuction:Auction,replyTo: ActorRef[String]) extends InternalCommand
  private case class InternalEndAuctionResponse[A <: ReplicatedData](rsp: UpdateResponse[A],auctionId:String) extends InternalCommand
  private case class InternalCancelAuctionResponse[A <: ReplicatedData](rsp: UpdateResponse[A],replyTo:ActorRef[String]) extends InternalCommand
  private case class InternalMakeBidResponse[A <: ReplicatedData](rsp: UpdateResponse[A],newBid:Bid,replyTo: ActorRef[String]) extends InternalCommand
  private case class InternalCheckAuctionEnded(auctionId:String, rsp: GetResponse[LWWMap[String, AuctionActorState]]) extends InternalCommand
  private case class InternalNotifyNewPrice(newBid:Bid, replyTo: ActorRef[String], rsp: GetResponse[LWWMap[String, AuctionActorState]]) extends InternalCommand
  private case object Tick extends Command

  def apply(index: Long,auctionActorServiceKey : ServiceKey[Command]): Behavior[Command] =
    Behaviors.setup(ctx => {
      Behaviors.withTimers[Command] { timers =>
        timers.cancelAll()
        timers.startTimerWithFixedDelay(Tick, Tick, 1.second)
        new AuctionActor(ctx, index, auctionActorServiceKey)
      }
    }
  )
}

private class AuctionActor(
                            context: ActorContext[Command],
                            index: Long,
                            auctionActorServiceKey: ServiceKey[Command]
                          ) extends AbstractBehavior[Command](context) {


  var id : String = _
  var auctionActorState: AuctionActorState = _

  implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress
  val DataKey: LWWMapKey[String, AuctionActorState] = LWWMapKey[String, AuctionActorState]("auctionStatePool")

  private val readMajority = ReadMajority(getTimeout(context).duration)
  private val writeMajority = WriteMajority(getTimeout(context).duration)

  context.system.receptionist ! Receptionist.Register(auctionActorServiceKey, context.self)
  println(s"AuctionActor Registrado con índice $index")

  override def onMessage(msg: Command): Behavior[Command] =
      DistributedData.withReplicatorMessageAdapter[Command, LWWMap[String, AuctionActorState]] { replicator =>
        msg match {
          //Init Check Auction Ended
          case Tick =>
            if (this.id != null) {
              replicator.askGet(
                askReplyTo => Get(DataKey, readMajority, askReplyTo),
                rsp => InternalCheckAuctionEnded(this.id, rsp)
              )
            }
            Behaviors.same
          case InternalCheckAuctionEnded(auctionId, g @ GetSuccess(DataKey)) =>
            val data = g.get(DataKey)
            data.get(auctionId) match {
              case Some(auctionActorState) =>
                if (DateTime.now >= auctionActorState.endTime){
                  println(s"Ending idx $index auction $id")
                  context.self ! EndAuction(auctionId)
                }
              case None =>
            }
            Behaviors.same
          case InternalCheckAuctionEnded(_, NotFound(DataKey, _)) => Behaviors.same
          case InternalCheckAuctionEnded(_, GetFailure(DataKey, _)) => Behaviors.same
          //End Check Auction Ended



          // Init Resume Auction
          case ResumeAuction(auctionId) =>
            println(s"Resuming Auction $auctionId")
            this.id = auctionId
            Behaviors.same
          //



          //Init Start Auction
          case StartAuction(auctionId, newAuction, replyTo) =>
            println(s"Start Auction $auctionId")
            this.id = auctionId
            replicator.askUpdate(
              askReplyTo => Update(DataKey, LWWMap.empty[String, AuctionActorState], writeMajority, askReplyTo) {
                auctionStatePool => startAuction(auctionStatePool, auctionId, newAuction)
              },
              InternalStartAuctionResponse.apply(_, newAuction, replyTo))
            Behaviors.same

          case InternalStartAuctionResponse(_: UpdateSuccess[_], newAuction, replyTo) =>
            getOneActor(context, NotifierSpawnerActor.serviceKey, 3) match {
              case Some(actor) =>
                println(s"Notificando new auction ${newAuction.id}")
                actor ! NotifyNewAuction(newAuction)
                replyTo ! s"Auction index: $index"
              case None =>
                context.log.error("No se pudo obtener ref al NotifierSpawner :(")
                replyTo ! s"Error notificando para auction $index"
            }
            Behaviors.same

          case InternalStartAuctionResponse(_: UpdateTimeout[_], newAuction, _) =>
            context.log.error(s"Timeout actualizando estructura en StartAuction ${newAuction.id}")
            Behaviors.same
          case InternalStartAuctionResponse(e: UpdateFailure[_], _, _) => throw new IllegalStateException("Unexpected failure: " + e)
          //End Start Auction




          // Init MakeBid
          case MakeBid(newBid, replyTo) =>
            println("Make bid in auction "+newBid.auctionId)
            replicator.askUpdate(
              askReplyTo => Update(DataKey, LWWMap.empty[String, AuctionActorState], writeMajority, askReplyTo) {
                auctionStatePool => makeBid(auctionStatePool, newBid, replyTo)
              },
              InternalMakeBidResponse.apply(_, newBid, replyTo))
            Behaviors.same
          case InternalMakeBidResponse(_: UpdateSuccess[_], newBid, replyTo) =>
            replicator.askGet(
              askReplyTo => Get(DataKey, readMajority, askReplyTo),
              rsp => InternalNotifyNewPrice(newBid, replyTo,rsp)
            )
            Behaviors.same

          case InternalMakeBidResponse(_: UpdateTimeout[_], _, _) => Behaviors.same
          case InternalMakeBidResponse(e: UpdateFailure[_], _, _) => throw new IllegalStateException("Unexpected failure: " + e)
          case InternalNotifyNewPrice(newBid, replyTo ,g @ GetSuccess(DataKey)) =>
            val data = g.get(DataKey)
            data.get(newBid.auctionId) match {
              case Some(auctionActorState) =>
                if(newBid.price == auctionActorState.price && newBid.buyerName == auctionActorState.currentWinner) {
                  notifyNewPrice(auctionActorState.auction, auctionActorState.buyers, auctionActorState.price)
                  replyTo ! s"El nuevo precio es: |${auctionActorState.price}| y el ganador por el momento es |${auctionActorState.currentWinner}|"
                }
              case None => replyTo ! s"No se pudo encontrar la subaste ${newBid.auctionId} :("
            }
            Behaviors.same
          case InternalNotifyNewPrice(_, _, NotFound(DataKey, _)) => Behaviors.same
          case InternalNotifyNewPrice(_, _, GetFailure(DataKey, _)) => Behaviors.same
          //End Make Bid



          //Init End Auction
          case EndAuction(auctionId) =>
            println(s"End Auction $auctionId")
            replicator.askUpdate(
              askReplyTo => Update(DataKey, LWWMap.empty[String, AuctionActorState], writeMajority, askReplyTo) {
                auctionStatePool => {
                  auctionStatePool.get(auctionId) match {
                    case Some(someAuctionActorState) =>
                      this.auctionActorState = someAuctionActorState
                      endAuction(auctionStatePool, auctionId)
                    case None => auctionStatePool
                  }
                }
              },
              InternalEndAuctionResponse.apply(_, auctionId))
            Behaviors.same
          case InternalEndAuctionResponse(_: UpdateSuccess[_], _) =>
            if (null != auctionActorState.currentWinner) {
              notifyLosers(this.auctionActorState.currentWinner)
              notifyWinner()
            }
            freeAuction(this.id)
            Behaviors.same

          case InternalEndAuctionResponse(_: UpdateTimeout[_], _) => Behaviors.same
          case InternalEndAuctionResponse(e: UpdateFailure[_], _) => throw new IllegalStateException("Unexpected failure: " + e)
          //End End Auction



          //Init Delete Auction
          case CancelAuction(replyTo) =>
            println(s"Cancelando subasta $id")
            replicator.askUpdate(
              askReplyTo => Update(DataKey, LWWMap.empty[String, AuctionActorState], writeMajority, askReplyTo) {
                auctionStatePool => {
                  auctionStatePool.get(id) match {
                    case Some(someAuctionActorState) =>
                      this.auctionActorState = someAuctionActorState
                      endAuction(auctionStatePool, id)
                    case None => auctionStatePool
                  }
                }
              },
              InternalCancelAuctionResponse.apply(_, replyTo))
            Behaviors.same
          case InternalCancelAuctionResponse(_: UpdateSuccess[_], replyTo) =>
            notifyCancelledAuction()
            replyTo ! s"Subasta ${this.id} cancelada"
            freeAuction(this.id)
            Behaviors.same

          case InternalCancelAuctionResponse(_: UpdateTimeout[_], _) =>
            freeAuction(this.id)
            Behaviors.same

          case InternalCancelAuctionResponse(e: UpdateFailure[_], _) => throw new IllegalStateException("Unexpected failure: " + e)
          //End Delete Auction
        }
      }

  def makeBid(auctionStatePool:LWWMap[String, AuctionActorState], newBid:Bid, replyTo: ActorRef[String]) : LWWMap[String, AuctionActorState] = {
      auctionStatePool.get(newBid.auctionId) match {
        case Some(auctionActorState) =>
          if (DateTime.now < auctionActorState.endTime) {
            var buyers = auctionActorState.buyers
            buyers += newBid.buyerName
            println(s"A punto de actualizar estado con lista de buyers: $buyers")

            if(newBid.price > auctionActorState.price){
              val a = auctionStatePool.remove(node,newBid.auctionId)
              a :+ (auctionActorState.auction.id -> AuctionActorState(auctionActorState.auction, newBid.price, newBid.buyerName,buyers,auctionActorState.endTime))
            } else {
              replyTo ! s"El precio enviado |${newBid.price}| puede ser menor que el establecido |${auctionActorState.price}|"
              val a = auctionStatePool.remove(node,newBid.auctionId)
              a :+ (auctionActorState.auction.id -> AuctionActorState(auctionActorState.auction, auctionActorState.price, auctionActorState.currentWinner,buyers,auctionActorState.endTime))
            }
          } else {
            replyTo ! s"Subasta ya terminada ${newBid.auctionId}"
            auctionStatePool
          }
        case None => auctionStatePool
      }
  }

  def startAuction(auctionStatePool:LWWMap[String, AuctionActorState], auctionId:String, newAuction: Auction) : LWWMap[String, AuctionActorState] = {
    val a = auctionStatePool.remove(node,auctionId)
    val endTime : DateTime = DateTime.now + (newAuction.duration * 1000)
    a :+ (auctionId -> AuctionActorState(newAuction, newAuction.basePrice, null,Set[String](),endTime))
  }

  def endAuction(auctionStatePool:LWWMap[String, AuctionActorState], auctionId:String): LWWMap[String, AuctionActorState] = {
    auctionStatePool.remove(node,auctionId)
  }

  def notifyWinner(): Unit = {
    println(s"Auction a punto de notificar a ganador")
    getOneActor(context, NotifierSpawnerActor.serviceKey) match {
      case Some(actor) => actor ! NotifyWinner(this.auctionActorState.auction, this.auctionActorState.currentWinner)
      case None => context.log.debug("No se pudo obtener ref al NotifierSpawner :(")
    }
  }

  def notifyLosers(currentWinner : String): Unit = {
    println(s"Ganador actual: ${this.auctionActorState.currentWinner}")
    println(s"Buyers a filtrar: ${this.auctionActorState.buyers}")
    val losers = this.auctionActorState.buyers.filter(b => ! b.equals(currentWinner))
    println(s"Auction a punto de notificar perdedores: $losers")
    getOneActor(context, NotifierSpawnerActor.serviceKey) match {
      case Some(actor) => actor ! NotifyLosers(this.auctionActorState.auction, losers)
      case None => context.log.debug("No se pudo obtener ref al NotifierSpawner :(")
    }
  }

  def notifyCancelledAuction(): Unit = {
    getOneActor(context, NotifierSpawnerActor.serviceKey) match {
      case Some(actor) => actor ! NotifyCancellation(auctionActorState.auction, auctionActorState.buyers)
      case None => context.log.debug("No se pudo obtener ref al NotifierSpawner :(")
    }
  }

  def notifyNewPrice(auction:Auction, buyers:Set[String], newPrice:Double): Unit = {
    println(s"Avisando nuevo precio a $buyers")
    getOneActor(context, NotifierSpawnerActor.serviceKey) match {
      case Some(actor) => actor ! NotifyNewPrice(auction, buyers, newPrice)
      case None => context.log.debug("No se pudo obtener ref al NotifierSpawner :(")
    }
  }

  def freeAuction(auctionId:String): Unit = {
    getOneActor(context, AuctionSpawnerActor.serviceKey) match {
      case Some(actor) =>
        actor ! FreeAuction(auctionId)
        this.id = null
        this.auctionActorState = null
      case None => context.log.debug("No se pudo obtener ref al AuctionSpawner :(")
    }
  }


}