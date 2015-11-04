package net.rainmore.aggregator

import akka.actor.{Terminated, ReceiveTimeout, ActorLogging, Actor, ActorRef, Props}
import net.rainmore.{Notification, Id, Sqs}
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

object JobWorker {

    val name = "aggregator-worker"

    def props = Props(new JobWorker)

    case class Work(master: ActorRef)
    case class Task(messages: List[Sqs], master: ActorRef)
    case object WorkLoadDepleted
}

class JobWorker extends Actor with ActorLogging {
    import JobWorker._
    import context._

    var processed = 0

    def receive = idle

    def idle: Receive = {
        case Work(master) =>
            become(enlisted(master))

            master ! JobMaster.Enlist(self)
            master ! JobMaster.NextTask
            watch(master)

            setReceiveTimeout(30 seconds)
    }

    def enlisted(master: ActorRef): Receive = {
        case ReceiveTimeout =>
            master ! JobMaster.NextTask

        case Task(messages, master) =>
            val countMap = processTask(messages)
            processed = processed + 1
            master ! JobMaster.TaskResult(countMap)
            master ! JobMaster.NextTask

        case WorkLoadDepleted =>
            setReceiveTimeout(Duration.Undefined)
            become(retired)

        case Terminated(master) =>
            setReceiveTimeout(Duration.Undefined)
            stop(self)
    }


    def retired: Receive = {
        case Terminated(master) =>
            stop(self)
        case _ => log.error("I'm retired.")
    }

    def processTask(messages: List[Sqs]): Map[Id, ListBuffer[Notification]] = {
        messages.foldLeft(Map.empty[Id, ListBuffer[Notification]]){(map, sqs) =>
            map + (sqs.id -> (map.getOrElse(sqs.id, ListBuffer[Notification]()) += sqs.toNotification))
        }
    }
}
