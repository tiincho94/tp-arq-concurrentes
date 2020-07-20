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
  final case class GetBuyers(tags: Set[String] = Set(), replyTo: ActorRef[Buyers]) extends BuyersSubscriptorCommand
  final case class CreateBuyer(buyer:Buyer, replyTo: ActorRef[String]) extends BuyersSubscriptorCommand

  // instanciación del objeto
  def apply(): Behavior[BuyersSubscriptorCommand] =
    Behaviors.setup { ctx =>
      ctx.log.info("Configurando BuyerSubscriptor")
      ctx.system.receptionist ! Receptionist.Register(BuyersSubscriptorServiceKey, ctx.self)
      Behaviors.receiveMessage {
        case GetBuyer(name,replyTo)=>
          buyersSet.find(buyer => buyer.name == name) match {
            case Some(buyer) => replyTo ! buyer
            case None => throw new IllegalArgumentException(s"buyer no encontrado $name")
          }
          Behaviors.same
        case GetBuyers(tags, replyTo) =>
          if (tags.isEmpty) {
            replyTo ! Buyers(this.buyersSet)
          } else {
            replyTo ! filterBuyers(tags)
          }
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

  /**
   * @param tags
   * @return buyers que tengan cualquiera de los tags provistos
   */
  def filterBuyers(tags: Set[String]): Buyers = {
    var buyers = Set[Buyer]()
    this.buyersSet.foreach(buyer => {
      for (tag <- tags) {
        if (buyer.tags.contains(tag)) {
          buyers += buyer
        }
      }
    })
    Buyers(buyers)
  }
}
