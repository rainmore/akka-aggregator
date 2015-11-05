package net.rainmore.cluster

import akka.cluster.{MemberStatus, Cluster}
import akka.cluster.ClusterEvent._
import akka.actor.Actor.Receive
import akka.actor.{ActorLogging, Actor}
class ClusterListener extends Actor with ActorLogging {


    val cluster = Cluster(context.system)
    cluster.subscribe(self, classOf[ClusterDomainEvent])

//    @throws[Exception](classOf[Exception])
//    override def preStart(): Unit = {
//        cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
//    }

    @throws[Exception](classOf[Exception])
    override def postStop(): Unit = {
        cluster.unsubscribe(self)
        super.postStop()
    }

    def receive ={
        case MemberUp(member) =>
            log.info(s"$member UP.")
        case MemberExited(member)=>
            member.isOlderThan()
            log.info(s"$member EXITED.")
        case MemberRemoved(member, previousState)=>
            if(previousState == MemberStatus.Exiting) {
                log.info(s"Member $member Previously gracefully exited, REMOVED.")
            } else {
                log.info(s"$member Previously downed after unreachable, REMOVED.")
            }
        case UnreachableMember(member) =>
            log.info(s"$member UNREACHABLE")
        case ReachableMember(member) =>
            log.info(s"$member REACHABLE")
        case state: CurrentClusterState =>
            log.info(s"Current state of the cluster: $state")
    }
}
