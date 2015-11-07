package net.rainmore.hedwig

import akka.actor.Actor.Receive
import akka.actor.{ActorRef, ActorLogging, Actor, Props}

object Aggregator {
    val name = "aggregator"
    val props = Props(classOf[Aggregator])

    sealed trait Message
    case class Start(receiver: ActorRef) extends Message
    case class Aggregate(messages: List[String]) extends Message
    object Finish extends Message
}

class Aggregator extends Actor with ActorLogging {

    override def receive: Receive = {
        case Aggregator.Start(receiver) => {
            receiver ! SqsReceiver.Collect(self)
        }
        case Aggregator.Aggregate(messages) => {
            val result = messages.mkString(", ")
            log.info("=========== Result: %s".format(result))
        }
    }
}
