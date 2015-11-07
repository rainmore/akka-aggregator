package net.rainmore.cluster

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit}
import com.typesafe.scalalogging.LazyLogging
import net.rainmore.cluster.SilentActor.{GetState, SilentMessage}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, Suite, WordSpecLike}

trait StopSystemAfterAll extends BeforeAndAfterAll { this: TestKit with Suite =>

    override protected def afterAll() {
        super.afterAll()
        system.terminate()
    }

}

object SilentActor {
    case class SilentMessage(message: String)
    case class GetState(receiver: ActorRef)

}

class SilentActor extends Actor {
    import SilentActor._
    var internalState = Vector[String]()

    def receive = {
        case SilentMessage(data) =>
            internalState = internalState :+ data
        case GetState(receiver) =>
            receiver ! internalState
    }

    def state = internalState
}

class SilentActor01Test extends TestKit(ActorSystem("testsystem"))
with WordSpecLike
with MustMatchers
with StopSystemAfterAll with LazyLogging {

    "A Silent Actor" must {
        "change state when it receives a message, single threaded" in {
            val silentActor = TestActorRef[SilentActor]
            silentActor ! SilentMessage("whisper")

            logger.info("state: {}", silentActor.underlyingActor.state)

            silentActor.underlyingActor.state must(contain("whisper"))

        }
        "change state when it receives a message, multi-threaded" in {
            val silentActor = system.actorOf(Props[SilentActor], "s3")
            silentActor ! SilentMessage("whisper1")
            silentActor ! SilentMessage("whisper2")
            silentActor ! GetState(testActor)

            expectMsg(Vector("whisper1", "whisper2"))
        }
    }

}