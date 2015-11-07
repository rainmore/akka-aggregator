package net.rainmore.hedwig

import akka.actor.Actor.Receive
import akka.actor.{ActorRef, ActorLogging, Actor, Props}

import scala.collection.mutable.ListBuffer
import scala.util.Random

object SqsReceiver {
    val name = "sqs-receiver"
    val props = Props(classOf[SqsReceiver])

    sealed trait Message
    object Start extends Message
    object Reset extends Message
    case class Collect(master: ActorRef) extends Message
//    object Finish
}

class SqsReceiver extends Actor with ActorLogging {

    private var messages: ListBuffer[String] = ListBuffer[String]()

    override def receive: Receive = {
        case SqsReceiver.Start => {
            log.info("================= collect one message")
            collect
        }
        case SqsReceiver.Collect(master) => {
            log.info("================= delivery all messages ")
            master ! Aggregator.Aggregate(messages.toList)
            reset
        }
    }


    private def collect: Unit = messages += Random.nextString(10)

    private def reset: Unit = messages = ListBuffer[String]()
}
