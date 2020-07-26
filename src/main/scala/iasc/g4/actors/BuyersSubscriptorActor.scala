package iasc.g4.actors

//import akka.actor.typed.delivery.internal.ProducerControllerImpl.InternalCommand

import scala.concurrent.duration._
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import iasc.g4.CborSerializable
import iasc.g4.models.Models.{Buyer, Buyers, Command, InternalCommand, OperationPerformed}
import akka.cluster.ddata.{Flag, LWWMap, LWWMapKey, ReplicatedData, SelfUniqueAddress}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.{Get, Update}

/**
 * Actor que suscribe un nuevo comprador y mantiene una lista con los mismos.</br>
 * También permite consultar la lista de compradores
 */
object BuyersSubscriptorActor {

  trait BuyersSubscriptorCommand extends Command

  val BuyersSubscriptorServiceKey = ServiceKey[BuyersSubscriptorCommand]("BuyerSubscriptor")

  //var buyersSet = Set[Buyer]()


  final case class GetBuyer(name: String, replyTo: ActorRef[Option[Buyer]]) extends BuyersSubscriptorCommand

  final case class GetBuyers(tags: Set[String] = Set(), replyTo: ActorRef[Buyers]) extends BuyersSubscriptorCommand

  final case class CreateBuyer(buyer: Buyer, replyTo: ActorRef[String]) extends BuyersSubscriptorCommand

  private case class InternalUpdateResponse[A <: ReplicatedData](rsp: UpdateResponse[A]) extends InternalCommand
  private case class InternalBuyersGetResponse(tags: Set[String], replyTo: ActorRef[Buyers], rsp: GetResponse[LWWMap[String, Buyer]]) extends InternalCommand

  private case class InternalBuyerGetResponse(name:String, replyTo: ActorRef[Option[Buyer]], rsp: GetResponse[LWWMap[String, Buyer]]) extends InternalCommand


  private val timeout = 3.seconds
  private val readMajority = ReadMajority(timeout)
  private val writeMajority = WriteMajority(timeout)

  // instanciación del objeto
  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      DistributedData.withReplicatorMessageAdapter[Command, Flag] { replicatorFlag =>
        DistributedData.withReplicatorMessageAdapter[Command, LWWMap[String, Buyer]] { replicator =>

          implicit val node: SelfUniqueAddress = DistributedData(ctx.system).selfUniqueAddress

          val DataKey = LWWMapKey[String, Buyer]("buyer")

          ctx.log.info("Configurando BuyerSubscriptor")
          ctx.system.receptionist ! Receptionist.Register(BuyersSubscriptorServiceKey, ctx.self)

          def updateBuyers(data: LWWMap[String, Buyer], buyer: Buyer, replyTo: ActorRef[String]): LWWMap[String, Buyer] = {
            data.get(buyer.name) match {
              case Some(_) => {
                replyTo ! "Nombre no disponible"
                data
              }
              case None => {
                replyTo ! "Buyer creado"
                data :+ (buyer.name -> buyer)
              }
            }
          }

          /**
           * @param buyers
           * @param tags
           * @return buyers que tengan cualquiera de los tags provistos
           */
          def filterBuyers(buyers: Set[Buyer], tags: Set[String]): Buyers = {
            if (tags.isEmpty) {
              Buyers(buyers)
            } else {
              var result = Set[Buyer]()
              buyers.foreach(buyer => for (tag <- tags) if (buyer.tags.contains(tag)) result += buyer)
              Buyers(result)
            }
          }

          Behaviors.receiveMessage {
            case GetBuyer(name, replyTo) =>
              replicator.askGet(
                askReplyTo => Get(DataKey, readMajority, askReplyTo),
                rsp => InternalBuyerGetResponse(name, replyTo, rsp)
              )
              Behaviors.same

            case InternalBuyerGetResponse(name, replyTo, g @ GetSuccess(DataKey, _)) =>
              val data = g.get(DataKey)
              val buyers = data.entries.values.toSet
              replyTo ! buyers.find(buyer => buyer.name == name)
              Behaviors.same

            case InternalBuyerGetResponse(name, replyTo, NotFound(DataKey, _)) =>
              replyTo ! None
              Behaviors.same

            case InternalBuyerGetResponse(name, replyTo, GetFailure(DataKey, _)) =>
              // ReadMajority failure, try again with local read
              replicator.askGet(
                askReplyTo => Get(DataKey, ReadLocal, askReplyTo),
                rsp => InternalBuyerGetResponse(name, replyTo, rsp))
              Behaviors.same

            case InternalUpdateResponse(_: UpdateSuccess[_]) => Behaviors.same
            case InternalUpdateResponse(_: UpdateTimeout[_]) => Behaviors.same
            case InternalUpdateResponse(e: UpdateFailure[_]) => throw new IllegalStateException("Unexpected failure: " + e)

            case GetBuyers(tags, replyTo) =>
              replicator.askGet(
                askReplyTo => Get(DataKey, readMajority, askReplyTo),
                rsp => InternalBuyersGetResponse(tags, replyTo, rsp)
              )
              Behaviors.same

            case InternalBuyersGetResponse(tags, replyTo, g @ GetSuccess(DataKey, _)) =>
              val data = g.get(DataKey)
              replyTo ! filterBuyers(data.entries.values.toSet, tags)
              Behaviors.same

            case InternalBuyersGetResponse(tags, replyTo, NotFound(DataKey, _)) =>
              replyTo ! Buyers(Set.empty)
              Behaviors.same

            case InternalBuyersGetResponse(tags, replyTo, GetFailure(DataKey, _)) =>
              // ReadMajority failure, try again with local read
              replicator.askGet(
                askReplyTo => Get(DataKey, readMajority, askReplyTo),
                rsp => InternalBuyersGetResponse(tags, replyTo, rsp))
              Behaviors.same

            case CreateBuyer(buyer, replyTo) =>
              println("create buyer...")
              replicator.askUpdate(
                askReplyTo => Update(DataKey, LWWMap.empty[String, Buyer], writeMajority, askReplyTo) {
                  buyerSet => updateBuyers(buyerSet, buyer, replyTo)
                },
                InternalUpdateResponse.apply)
              Behaviors.same

            case default =>
              println(default)
              println("Ignorando mensaje inesperado...")
              Behaviors.same
          }
        }
      }
    }
}