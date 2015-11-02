package net.rainmore.aggregator

import akka.actor.{Terminated, ReceiveTimeout, ActorLogging, Actor, ActorRef, Props}
import net.rainmore.{Notification, Id, Sqs}
import scala.collection.mutable
import scala.concurrent.duration._

object JobWorker {
    def props = Props(new JobWorker)

    case class Work(master: ActorRef)
    case class Task(messages: Set[Sqs], master: ActorRef)
    case object WorkLoadDepleted
}

class JobWorker extends Actor with ActorLogging {
    import JobMaster._
    import JobWorker._
    import context._

    var processed = 0

    def receive = idle

    def idle: Receive = {
        case Work(master) =>
            become(enlisted(master))

            master ! Enlist(self)
            master ! NextTask
            watch(master)

            setReceiveTimeout(30 seconds)
    }

    def enlisted(master: ActorRef): Receive = {
        case ReceiveTimeout =>
            master ! NextTask

        case Task(messages, master) =>
            val countMap = processTask(messages)
            processed = processed + 1
            master ! TaskResult(countMap)
            master ! NextTask

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

    def processTask(messages: Set[Sqs]): Map[Id, Vector[Notification]] = {
        val result = Map.empty[Id, Vector[Notification]]
        messages.foreach(sqs => {
            if (!result.contains(sqs.id)) {
                result + (sqs.id -> Vector[Notification]())
            }
            result.get(sqs.id).get :+ (sqs.toNotification)
        })
        result
    }
}
