package net.rainmore.hedwig.aggregator

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, ReceiveTimeout, SupervisorStrategy, Terminated}
import akka.routing.{DefaultResizer, RoundRobinPool}
import net.rainmore.hedwig.{Notification, Recipient}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.language.postfixOps

object JobMaster {
    def props = Props(new JobMaster)

    private final val sqsGroupSize = 10
    private final val workerTimeOut = 60 seconds

    case class StartJob(name: String, messages: List[Notification])
    case class Enlist(worker: ActorRef)

    case object NextTask
    case class TaskResult(map: Map[Recipient, ListBuffer[Notification]])

    case object Start
    case object MergeResults
}

class JobMaster extends Actor
with ActorLogging
with CreateWorkerRouter {
    import JobMaster._
    import JobReceptionist.Aggregate
    import context._

    var sqsParts = Vector[List[Notification]]()
    var intermediateResult = Vector[Map[Recipient, ListBuffer[Notification]]]()
    var workGiven = 0
    var workReceived = 0
    var workers = Set[ActorRef]()

    val router = createWorkerRouter

    override def supervisorStrategy: SupervisorStrategy =
        SupervisorStrategy.stoppingStrategy

    def receive = idle

    def idle: Receive = {
        case StartJob(jobName, messages) =>
            sqsParts = messages.grouped(sqsGroupSize).toVector
            val cancellable = context.system.scheduler.schedule(0 millis, 1000 millis, router, JobWorker.Work(self))
            context.setReceiveTimeout(workerTimeOut)
            become(working(jobName, sender, cancellable))
    }

    def working(jobName: String,
                receptionist: ActorRef,
                cancellable: Cancellable): Receive = {
        case Enlist(worker) =>
            watch(worker)
            workers  = workers + worker

        case NextTask =>
            if(sqsParts.isEmpty) {
                sender() ! JobWorker.WorkLoadDepleted
            } else {
                sender() ! JobWorker.Task(sqsParts.head, self)
                workGiven = workGiven + 1
                sqsParts = sqsParts.tail
            }

        case TaskResult(countMap) =>
            intermediateResult = intermediateResult :+ countMap
            workReceived = workReceived + 1

            if(sqsParts.isEmpty && workGiven == workReceived) {
                cancellable.cancel()
                become(finishing(jobName, receptionist, workers))
                setReceiveTimeout(Duration.Undefined)
                self ! MergeResults
            }

        case ReceiveTimeout =>
            if(workers.isEmpty) {
                stop(self)
            } else setReceiveTimeout(Duration.Undefined)

        case Terminated(worker) =>
            stop(self)
    }

    def finishing(jobName: String,
                  receptionist: ActorRef,
                  workers: Set[ActorRef]): Receive = {
        case MergeResults =>
            val mergedMap = merge()
            workers.foreach(stop(_))
            receptionist ! Aggregate(jobName, mergedMap)

        case Terminated(worker) =>
            log.info(s"Job $jobName is finishing. Worker ${worker.path.name} is stopped.")
    }

    def merge(): Map[Recipient, ListBuffer[Notification]] = {
        intermediateResult.foldLeft(Map[Recipient, ListBuffer[Notification]]()) {
            (start, current) =>
                start.map {
                    case (id: Recipient, buffer: ListBuffer[Notification]) =>
                        current.get(id).map(list => ( id -> (buffer ++= list))).getOrElse(id -> buffer)
                } ++ (current -- start.keys)
        }
    }
}


trait CreateWorkerRouter { this: Actor =>
    val routerLowbound = 5
    val routerUpperbound = 20


    def createWorkerRouter: ActorRef = {

        val router = new RoundRobinPool(routerLowbound).withResizer(new DefaultResizer(routerLowbound, routerUpperbound))
        context.actorOf(JobWorker.props.withRouter(router), "aggregator-router")
//
//
//        context.actorOf(
//            ClusterRouterPool(BroadcastPool(10), ClusterRouterPoolSettings(
//                totalInstances = 100, maxInstancesPerNode = 20,
//                allowLocalRoutees = false, useRole = None)).props(Props[JobWorker]),
//            name = "aggregator-router")
    }
}