package net.rainmore.aggregator

import akka.actor.{Cancellable, ReceiveTimeout, Terminated, SupervisorStrategy, ActorLogging, Actor, ActorRef, Props}
import akka.cluster.routing.{ClusterRouterPoolSettings, ClusterRouterPool}
import akka.routing.BroadcastPool
import net.rainmore.{Notification, Id, Sqs}
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

object JobMaster {
    def props = Props(new JobMaster)

    private final val sqsGroupSize = 10
    private final val workerTimeOut = 60 seconds

    case class StartJob(name: String, messages: List[Sqs])
    case class Enlist(worker: ActorRef)

    case object NextTask
    case class TaskResult(map: Map[Id, ListBuffer[Notification]])

    case object Start
    case object MergeResults
}

class JobMaster extends Actor
with ActorLogging
with CreateWorkerRouter {
    import JobReceptionist.Aggregate
    import JobMaster._
    import context._

    var sqsParts = Vector[List[Sqs]]()
    var intermediateResult = Vector[Map[Id, ListBuffer[Notification]]]()
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

    def merge(): Map[Id, ListBuffer[Notification]] = {
        intermediateResult.foldLeft(Map[Id, ListBuffer[Notification]]()) {
            (start, current) =>
                start.map {
                    case (id: Id, buffer: ListBuffer[Notification]) =>
                        current.get(id).map(list => ( id -> (buffer ++= list))).getOrElse(id -> buffer)
                } ++ (current -- start.keys)
        }
    }
}


trait CreateWorkerRouter { this: Actor =>
    def createWorkerRouter: ActorRef = {
        // TODO to replace this with resizable RoundRobinPool or local BroadcastPool
        context.actorOf(
            ClusterRouterPool(BroadcastPool(10), ClusterRouterPoolSettings(
                totalInstances = 100, maxInstancesPerNode = 20,
                allowLocalRoutees = false, useRole = None)).props(Props[JobWorker]),
            name = "aggregator-router")
    }
}