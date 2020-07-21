package iasc.g4.actors

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import iasc.g4.models.Models.{Auction, Buyer, Buyers, Command}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers}
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.Behavior
import iasc.g4.util.Util.{getOneActor, getTimeout}
import akka.actor.typed.scaladsl.AskPattern._
import iasc.g4.actors.BuyersSubscriptorActor.{GetBuyer, GetBuyers}

import scala.concurrent.{Await, Future}

/**
 * Encargado de asignar a actores Notifier la tarea de enviar una notificación, previamente obeniendo la lista de
 * compradores del BuyersSubscriptor
 */
object NotifierSpawnerActor {

  // TODO manter un pool fijo y reutilizarlos

  trait NotifierSpawnerCommand extends Command

  final case class NotifyNewAuction(auction: Auction) extends NotifierSpawnerCommand
  final case class NotifyNewPrice(auction: Auction, buyers: Set[String], newPrice: Double) extends NotifierSpawnerCommand
  final case class NotifyWinner(auction: Auction, winnerName: String) extends NotifierSpawnerCommand
  final case class NotifyLosers(auction: Auction, loosers: Set[String]) extends NotifierSpawnerCommand
  final case class NotifyCancellation(auction: Auction, buyers: Set[String]) extends NotifierSpawnerCommand

  val NotifierSpawnerServiceKey = ServiceKey[NotifierSpawnerCommand]("NotifierSpawner")

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      ctx.log.info("Configurando NotifierSpawnerActor")
      ctx.system.receptionist ! Receptionist.Register(NotifierSpawnerServiceKey, ctx.self)

      val pool = Routers.pool(poolSize = 5)(
        Behaviors.supervise(NotifierActor()).onFailure[Exception](SupervisorStrategy.restart)
      )
      val router = ctx.spawn(pool, "notifier-pool")

      Behaviors.receiveMessage {
        case NotifyNewAuction(auction) =>
          getBuyers(ctx, auction).buyers.foreach { buyer =>
            router ! NotifierActor.NewAuction(buyer, auction)
          }
          Behaviors.same
        case NotifyNewPrice(auction, buyers, newPrice) =>
          buyers.foreach(buyer => {
            router ! NotifierActor.NewPrice(getBuyer(ctx, buyer), newPrice, auction)
          })
          Behaviors.same
        case NotifyWinner(auction, winnerName) =>
          router ! NotifierActor.Winner(getBuyer(ctx, winnerName), auction)
          Behaviors.same
        case NotifyLosers(auction, loosers) =>
          loosers.foreach(looser => {
            router ! NotifierActor.Looser(getBuyer(ctx, looser), auction)
          })
          Behaviors.same
        case NotifyCancellation(auction, buyers) =>
          buyers.foreach { buyer =>
            router ! NotifierActor.Cancellation(getBuyer(ctx, buyer), auction)
          }
          Behaviors.same
      }
    }

  def getBuyers(ctx: ActorContext[_], auction: Auction): Buyers = {
    val actor = getOneActor(ctx, BuyersSubscriptorActor.BuyersSubscriptorServiceKey)
    val f = actor.ask(GetBuyers(auction.tags, _))(getTimeout(ctx), ctx.system.scheduler)
    Await.result(f, getTimeout(ctx).duration)
  }

  def getBuyer(ctx: ActorContext[_], name: String): Buyer = {
    val actor = getOneActor(ctx, BuyersSubscriptorActor.BuyersSubscriptorServiceKey)
    val f = actor.ask(GetBuyer(name, _))(getTimeout(ctx), ctx.system.scheduler)
    Await.result(f, getTimeout(ctx).duration)
  }
}













