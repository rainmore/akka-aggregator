package net.rainmore

import java.time.LocalDateTime

import akka.actor.Actor.Receive
import akka.contrib.pattern.Aggregator
import akka.event.Logging
import akka.routing.RoundRobinPool
import com.typesafe.config.{ConfigFactory, Config}
import com.typesafe.scalalogging.LazyLogging
import org.jfairy.Fairy
import scala.collection
import akka.actor._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random

case object Greet
case class WhoToGreet(who: String)
case class Greeting(message: String)
case object PushMessageTimeout


class Greeter extends Actor {
    var greeting = ""

    def receive = {
        case WhoToGreet(who) => greeting = s"hello, $who"
        case Greet           => sender ! Greeting(greeting) // Send the current greeting back to the sender
    }
}

// prints a greeting
class GreetPrinter extends Actor {
    def receive = {
        case Greeting(message) => println(message)
    }
}

case class MessageId (tenant: String, userId: Int)
case class SimpleMessage (id: MessageId, message: String)
case class PushMessage (id: MessageId, messages: List[String])

object MessageSender {

    val fairy = Fairy.create()

    private def userIds = {
        val r = 1 to 10
        r.toSet
    }

    val tenants = Map("a" -> userIds, "b" -> userIds, "c" -> userIds, "d" -> userIds)

    object Generate
    object Send
}

class MessageSender extends Actor with ActorLogging {
    def receive = {
        case MessageSender.Send => {
            MessageCollector.buffer.+=(generateMessage)
        }
    }

    def generateMessage: SimpleMessage = {
        val tenant: String = Random.shuffle(MessageSender.tenants.keys).head
        val userId: Int = Random.shuffle(MessageSender.tenants.get(tenant).get).head
        val message: String = MessageSender.fairy.textProducer().sentence()
        val st = "tenant: %s, userId: %s, message: %s".format(tenant, userId, message)
        log.info(st)
        println(st)
        SimpleMessage(new MessageId(tenant, userId), message)
    }
}

object MessageCollector {

    val buffer: mutable.ListBuffer[SimpleMessage] = new ListBuffer()

    object Collect
}

class MessageCollector extends Actor with ActorLogging {

    def receive = {
        case MessageCollector.Collect => {
            val st = "messages size: %s".format(MessageCollector.buffer.size)
//            println(st)
            log.info(st)
//            workers ! MessageSender.Send
        }
        case _ => log.error("invalid message in MessageCollector")
    }

}

object MessageAggregationProcessor {

    case class Push(pushMessage: PushMessage)
    case class Sum(simpleMessage: SimpleMessage)
    case object CantUnderstand
}

class MessageAggregationProcessor extends Actor with Aggregator with ActorLogging {
    import context._

    val publishers = context.actorOf(Props[MessagePublisher].withRouter(RoundRobinPool(3)))

//    override def expectOnce(fn: Actor.Receive): Actor.Receive = super.expectOnce(fn)
    expectOnce {
        case MessageAggregationProcessor.Sum(simpleMessage) =>
            new MessageAggregator(sender(), simpleMessage)
        case _ ⇒
            sender() ! MessageAggregationProcessor.CantUnderstand
            context.stop(self)
    }

    class MessageAggregator(originalSender: ActorRef,
                            simpleMessage: SimpleMessage) {

        val results1: Map[MessageId, mutable.ArrayBuffer[String]] = mutable.Map.empty()

        collect

        context.system.scheduler.scheduleOnce(1.second, self, PushMessageTimeout)
//        context.system.scheduler.schedule(10.second, 10.second, self, PushMessageTimeout)

        expect {
            case PushMessageTimeout ⇒ push
        }

        def push: Unit = {
            val pushMessages = results1.get(simpleMessage.id)
            results1.-(simpleMessage.id)
            if (pushMessages.isDefined) {
                originalSender ! MessagePublisher.Push(new PushMessage(simpleMessage.id, pushMessages.get.toList))
            }
        }

        def collect(): Unit = {
            if (!results1.contains(simpleMessage.id)) {
                results1.+(simpleMessage.id -> mutable.ArrayBuffer.empty[String])
            }
            //TODO: to understand what it does
//            context.stop(self)
            results1.get(simpleMessage.id).get.+(simpleMessage.message)
        }
    }
}

object MessagePublisher {

    case class Push(pushMessage: PushMessage)
}

class MessagePublisher extends Actor with ActorLogging {

    def receive = {
        case MessagePublisher.Push(pushMessage) =>
            println("%s:%s [%s]", pushMessage.tenant, pushMessage.userId, pushMessage.messages.mkString(", "))
    }
}

object Application extends App  with LazyLogging {
//    val config: Config = ConfigFactory.load()
//    val system = ActorSystem.create("aggregation")
//    val master = system.actorOf(MessageWorker.props(config), MessageWorker.name)


    // Create the 'helloakka' actor system
    val system = ActorSystem("helloakka")

    val sender1 = system.actorOf(Props[MessageSender], "sender1")
//    val sender2 = system.actorOf(Props[MessageSender], "sender2")
//    val sender3 = system.actorOf(Props[MessageSender], "sender3")
//    val sender4 = system.actorOf(Props[MessageSender], "sender4")

    val collector = system.actorOf(Props[MessageCollector])
    val aggregator = system.actorOf(Props[MessageAggregationProcessor])
    val publisher = system.actorOf(Props[MessagePublisher])

    system.scheduler.schedule(0.seconds, 1000.milliseconds, sender1, MessageSender.Send)
//    system.scheduler.schedule(0.seconds, 800.milliseconds, sender2, MessageSender.Send)
//    system.scheduler.schedule(0.seconds, 900.milliseconds, sender3, MessageSender.Send)
//    system.scheduler.schedule(0.seconds, 1100.milliseconds, sender4, MessageSender.Send)
    system.scheduler.schedule(5.seconds, 5.seconds, collector, MessageCollector.Collect)

//    // Create the 'greeter' actor
//    val greeter = system.actorOf(Props[Greeter], "greeter")
//
//    // Create an "actor-in-a-box"
//    val inbox = Inbox.create(system)
//
//    // Tell the 'greeter' to change its 'greeting' message
//    greeter.tell(WhoToGreet("akka"), ActorRef.noSender)
//
//    // Ask the 'greeter for the latest 'greeting'
//    // Reply should go to the "actor-in-a-box"
//    inbox.send(greeter, Greet)
//
//    // Wait 5 seconds for the reply with the 'greeting' message
//    val Greeting(message1) = inbox.receive(5.seconds)
//    println(s"Greeting: $message1")
//
//    // Change the greeting and ask for it again
//    greeter.tell(WhoToGreet("typesafe"), ActorRef.noSender)
//    inbox.send(greeter, Greet)
//    val Greeting(message2) = inbox.receive(5.seconds)
//    println(s"Greeting: $message2")
//
//    val greetPrinter = system.actorOf(Props[GreetPrinter])
//    // after zero seconds, send a Greet message every second to the greeter with a sender of the greetPrinter
//    system.scheduler.schedule(0.seconds, 1.second, greeter, Greet)(system.dispatcher, greetPrinter)

}

