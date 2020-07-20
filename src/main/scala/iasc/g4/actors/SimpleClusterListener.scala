package iasc.g4.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.ClusterEvent.{ClusterDomainEvent, MemberEvent, MemberJoined, MemberRemoved, MemberUp, UnreachableMember}
import akka.cluster.typed.Cluster
import akka.cluster.typed.Subscribe

object SimpleClusterListener {


  def apply(): Behavior[ClusterDomainEvent] =
    Behaviors.setup[ClusterDomainEvent] { context =>

      val cluster = Cluster(context.system)
      cluster.subscriptions ! Subscribe(context.self.ref, classOf[ClusterDomainEvent])


      context.log.info(s"started actor ${context.self.path} - (${context.self.getClass})")

      def running(): Behavior[ClusterDomainEvent] =
        Behaviors.receive { (context, message) =>
          message match {
            case MemberUp(member) =>
              context.log.info("Member is TINWOW Up: {}", member.address)
              println("Member is TINWOW Up: {}", member.address)
              Behaviors.same
            case UnreachableMember(member) =>
              context.log.info("Member detected TINWOW as unreachable: {}", member)
              println("Member detected TINWOW as unreachable: {}", member)
              Behaviors.same
            case MemberRemoved(member, previousStatus) =>
              context.log.info(
                "Member is TINWOW Removed: {} after {}",
                member.address, previousStatus)

              println(
                "Member is TINWOW Removed: {} after {}",
                member.address, previousStatus)
              Behaviors.same
            case _ =>
              Behaviors.same // ignore
          }
        }

      running()
    }
}