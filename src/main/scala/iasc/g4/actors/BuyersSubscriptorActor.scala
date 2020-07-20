package iasc.g4.actors

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import iasc.g4.CborSerializable
import iasc.g4.models.Models.{Buyer, Buyers, Command, OperationPerformed}

/**
 * Actor que suscribe un nuevo comprador y mantiene una lista con los mismos.</br>
 * También permite consultar la lista de compradores
 */
object BuyersSubscriptorActor {

  trait BuyersSubscriptorCommand extends Command

  val BuyersSubscriptorServiceKey = ServiceKey[BuyersSubscriptorCommand]("BuyerSubscriptor")

  var buyersSet = Set[Buyer]()

  final case class GetBuyer(name:String, replyTo: ActorRef[Buyer]) extends BuyersSubscriptorCommand
  final case class GetBuyers(replyTo: ActorRef[Buyers]) extends BuyersSubscriptorCommand
  final case class GetBuyersByTags(tags: Set[String],replyTo: ActorRef[Buyers]) extends BuyersSubscriptorCommand
  final case class CreateBuyer(buyer:Buyer, replyTo: ActorRef[String]) extends BuyersSubscriptorCommand

  // instanciación del objeto
  def apply(): Behavior[BuyersSubscriptorCommand] =
    Behaviors.setup { ctx =>
      ctx.log.info("Configurando BuyerSubscriptor")
      ctx.system.receptionist ! Receptionist.Register(BuyersSubscriptorServiceKey, ctx.self)
      Behaviors.receiveMessage {
        case GetBuyer(name,replyTo)=>
          var buyer = getBuyer(name)
          replyTo ! buyer
          Behaviors.same
        case GetBuyersByTags(tags, replyTo) =>
          //TODO implementar la búsqueda por tags
          replyTo ! Buyers(this.buyersSet)
          Behaviors.same
        case GetBuyers(replyTo) =>
          println("get buyers...")
          replyTo ! Buyers(this.buyersSet)
          Behaviors.same
        case CreateBuyer(buyer,replyTo) =>
          println("create buyer...")
          this.buyersSet+=buyer
          replyTo ! "Buyer creado"
          Behaviors.same
        case _ =>
          println("Ignorando mensaje inesperado...")
          Behaviors.same
      }
    }

  def getBuyer(name:String): Buyer ={
    var buyerSetAux = buyersSet.find(buyer => buyer.name == name)
    if (buyerSetAux==None) null
    else buyerSetAux.head
  }
}
