package net.rainmore.cluster

import akka.actor.Actor.Receive
import akka.actor._
import scala.concurrent.duration._

object MasterLookupProxy {
    private final val timeout = 5 seconds
}

class MasterLookupProxy(path: String) extends Actor with ActorLogging {
    import MasterLookupProxy._

    context.setReceiveTimeout(timeout)

    sendIdentifyRequest

    def sendIdentifyRequest: Unit = {
        val selection = context.actorSelection(path)
        selection ! Identify(path)
    }


    override def receive: Receive = identify

    private def identify: Receive = {
        case ActorIdentity(`path`, Some(master)) => {
            context.setReceiveTimeout(Duration.Undefined)
            log.info("switching to active state")
            context.become(active(master))
            context.watch(master)
        }
        case ActorIdentity(`path`, None) => {
            log.info("master doesn't exist")
            startMaster
        }
        case ReceiveTimeout => sendIdentifyRequest
        case message: Any => log.error(s"Ignoring message $message, not ready yet")
    }

    private def startMaster: Unit = {
        log.info("start the master")

        //TODO to start the master here
    }

    private def active(master: ActorRef): Receive = {
        case Terminated(actorRef) => {
            log.info("Actor $actorRef terminated.")
            context.become(identify)
            log.info("switching to identify state")
            context.setReceiveTimeout(timeout)
            sendIdentifyRequest
        }
        case message: Any => master forward message
    }
}
