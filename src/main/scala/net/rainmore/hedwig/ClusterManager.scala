package net.rainmore.hedwig

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.ClusterEvent._
import akka.cluster.{Member, Cluster, MemberStatus}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.language.postfixOps

object ClusterManager {
    val name = "cluster-manager"
    val props = Props(classOf[ClusterManager])
}

class ClusterManager extends Actor with ActorLogging {
    val cluster = Cluster(context.system)

    val receiver = context.system.actorOf(SqsReceiver.props, SqsReceiver.name)
    val aggregator = context.system.actorOf(Aggregator.props, Aggregator.name)


    @throws[Exception](classOf[Exception])
    override def preStart(): Unit = {
        log.info("======== Pre Start")
        cluster.subscribe(self, classOf[ClusterDomainEvent])
    }

    @throws[Exception](classOf[Exception])
    override def postStop(): Unit = {
        log.info("======== Post Stop")
        cluster.unsubscribe(self)
        super.postStop()
    }

    def receive ={
        case MemberUp(member) =>
            log.info(s">>>>>>>>>>>>>>$member UP.")
            if (checkLeader(member)) initAggregator
        case MemberExited(member)=>
            log.info(s">>>>>>>>>>>>>>$member EXITED.")
            checkLeader(member)
        case MemberRemoved(member, previousState)=>
            if(previousState == MemberStatus.Exiting) {
                log.info(s">>>>>>>>>>>>>>Member $member Previously gracefully exited, REMOVED.")
            } else {
                log.info(s">>>>>>>>>>>>>>$member Previously downed after unreachable, REMOVED.")
            }
            if (checkLeader(member)) initAggregator
        case UnreachableMember(member) =>
            log.info(s">>>>>>>>>>>>>>$member UNREACHABLE")
            if (checkLeader(member)) initAggregator
        case ReachableMember(member) =>
            log.info(s">>>>>>>>>>>>>>$member REACHABLE")
            checkLeader(member)
        case state: CurrentClusterState =>
            log.info(s">>>>>>>>>>>>>>Current state of the cluster: $state")
    }

    private def checkLeader(member: Member): Boolean = {
        log.info(s"<<<<<member: $member.address")
        log.info(s"<<<<<self: $cluster.selfAddress")
        log.info(s"<<<<<leader: $cluster.state.getLeader")
        member.address == cluster.selfAddress && cluster.selfAddress == cluster.state.getLeader
    }

    private def findAggregator: Unit = {
        import scala.concurrent.ExecutionContext.Implicits.global
        val url = "%s/user/%s".format(cluster.state.getLeader.toString, Aggregator.name)
        log.info("<<<<<<<<< master url %s".format(url))
        context.system.actorSelection(url).resolveOne(10.seconds).onComplete {
            case Success(actor) => {
                log.info("Master node exists")
            }
            case Failure(ex) => {
                log.info(">>>>>>>>>>>>>>>>>" + ex.getMessage)
                initAggregator
            }
        }
    }

    private def initAggregator: Unit = {
        log.info("========== start aggregator")
        import scala.concurrent.ExecutionContext.Implicits.global
        context.system.scheduler.schedule(0.seconds, 1.seconds, receiver, SqsReceiver.Start)
        context.system.scheduler.schedule(2.seconds, 10.seconds, aggregator, Aggregator.Start(receiver))
    }

}
