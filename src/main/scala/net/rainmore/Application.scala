//package net.rainmore
//
//import java.time.LocalDateTime
//
//import akka.actor.Actor.Receive
//import akka.contrib.pattern.Aggregator
//import akka.event.Logging
//import akka.routing.RoundRobinPool
//import com.typesafe.config.{ConfigFactory, Config}
//import com.typesafe.scalalogging.LazyLogging
//import org.jfairy.Fairy
//import scala.collection
//import akka.actor._
//import scala.collection.mutable
//import scala.collection.mutable.ListBuffer
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.duration._
//import scala.util.Random
//
//case object PushMessageTimeout
//
//case class MessageId (tenant: String, userId: Int)
//case class SimpleMessage (id: MessageId, message: String)
//case class PushMessage (id: MessageId, messages: List[String])
//
//object MessageSender {
//    val fairy = Fairy.create()
//
//    private def userIds = {
//        val r = 1 to 10
//        r.toSet
//    }
//
//    val tenants = Map("a" -> userIds, "b" -> userIds, "c" -> userIds, "d" -> userIds)
//
//    object Generate
//    object Send
//}
//
//class MessageSender extends Actor with ActorLogging {
//    def receive = {
//        case MessageSender.Send => {
//            MessageCollector.buffer.+=(generateMessage)
//        }
//        case _ => log.error("invalid message in MessageSender")
//    }
//
//    def generateMessage: SimpleMessage = {
//        val tenant: String = Random.shuffle(MessageSender.tenants.keys).head
//        val userId: Int = Random.shuffle(MessageSender.tenants.get(tenant).get).head
//        val message: String = MessageSender.fairy.textProducer().sentence()
//        val st = "tenant: %s, userId: %s, message: %s".format(tenant, userId, message)
////        log.info(st)
//        SimpleMessage(new MessageId(tenant, userId), message)
//    }
//}
//
//object MessageCollector {
//
//    val buffer: mutable.Buffer[SimpleMessage] = new ListBuffer[SimpleMessage]
//
//    object Collect
//    case class Push(pushMessages: List[PushMessage])
//}
//
//class MessageCollector extends Actor with ActorLogging {
//
//    val aggregator = context.actorOf(Props[MessageAggregationProcessor], "MessageAggregationProcessor")
//
//    def receive = {
//        case MessageCollector.Collect => {
//            val s = MessageCollector.buffer.size
//            val st = "messages size: %s".format(s)
//            log.info(st)
//            val size = {
//                if (s >= 10) 10
//                else s
//            }
//
//            if (s > 0) {
//                val list = MessageCollector.buffer.slice(0, size).toList
//                MessageCollector.buffer.remove(0, size)
//                //            val list = MessageCollector.buffer.splitAt(size).productIterator.toList.asInstanceOf[Iterator[SimpleMessage]]
//                aggregator ! MessageAggregationProcessor.Sum(list)
//            }
//
//        }
//        case _ => log.error("invalid message in MessageCollector")
//    }
//
//}
//
//object MessageAggregationProcessor {
//
//    case class Sum(simpleMessages: List[SimpleMessage])
//}
//
//class MessageAggregationProcessor extends Actor with Aggregator with ActorLogging {
//
//    val publishers = context.actorOf(Props[MessagePublisher].withRouter(RoundRobinPool(3)), "MessagePublisher")
////    override def expectOnce(fn: Actor.Receive): Actor.Receive = super.expectOnce(fn)
//    expectOnce {
//        case MessageAggregationProcessor.Sum(list) => {
//            log.info("simpleMessage: {}", list)
//            new MessageAggregator(sender(), list)
//        }
//        case MessageCollector.Push(pushMessage) => publishers ! MessagePublisher.Push(pushMessage)
//        case _ => log.error("1111 invalid message in MessageAggregationProcessor")
////            context.stop(self)
//    }
//
//    expect {
//        case MessageCollector.Push(pushMessage) => publishers ! MessagePublisher.Push(pushMessage)
//        case _ => log.error("2222 invalid message in MessageAggregationProcessor")
//    }
//
//    class MessageAggregator(originalSender: ActorRef,
//                            simpleMessages: List[SimpleMessage]) {
//
//        val results1 = new mutable.HashMap[MessageId, List[String]]()
//
//        collect()
//
////        context.system.scheduler.scheduleOnce(1.second, self, PushMessageTimeout)
//        context.system.scheduler.schedule(10.second, 10.second, self, PushMessageTimeout)
//
//        expect {
//            case PushMessageTimeout => push
//        }
//
//        def push(): Unit = {
//            val list = results1.map(i => {
//                new PushMessage(i._1, i._2)
//            }).toList
//            results1.clear()
//            originalSender ! MessageCollector.Push(list)
//        }
//
//        def collect(): Unit = {
//            simpleMessages.foreach(simpleMessage => {
//                if (!results1.contains(simpleMessage.id)) {
//                    results1.put(simpleMessage.id, List())
//                }
//                //TODO: to understand what it does
//                //            context.stop(self)
//                results1.get(simpleMessage.id).get.+(simpleMessage.message)
//            })
//        }
//    }
//}
//
//object MessagePublisher {
//
//    case class Push(pushMessages: List[PushMessage])
//}
//
//class MessagePublisher extends Actor with ActorLogging {
//
//    def receive = {
//        case MessagePublisher.Push(pushMessages) => {
//            pushMessages.foreach(pushMessage => log.info("===> %s:%s [%s] is pushed", pushMessage.id.tenant, pushMessage.id.userId, pushMessage.messages.mkString(", ")))
//
//        }
//
//    }
//}
//
//object Application extends App  with LazyLogging {
////    val config: Config = ConfigFactory.load()
////    val system = ActorSystem.create("aggregation")
////    val master = system.actorOf(MessageWorker.props(config), MessageWorker.name)
//
//
//    // Create the 'helloakka' actor system
//    val system = ActorSystem("helloakka")
//
//    val sender1 = system.actorOf(Props[MessageSender], "sender1")
////    val sender2 = system.actorOf(Props[MessageSender], "sender2")
////    val sender3 = system.actorOf(Props[MessageSender], "sender3")
////    val sender4 = system.actorOf(Props[MessageSender], "sender4")
//
//    val collector = system.actorOf(Props[MessageCollector], "MessageCollector")
//
//    system.scheduler.schedule(0.seconds, 1000.milliseconds, sender1, MessageSender.Send)
////    system.scheduler.schedule(0.seconds, 800.milliseconds, sender2, MessageSender.Send)
////    system.scheduler.schedule(0.seconds, 900.milliseconds, sender3, MessageSender.Send)
////    system.scheduler.schedule(0.seconds, 1100.milliseconds, sender4, MessageSender.Send)
//    system.scheduler.schedule(5.seconds, 5.seconds, collector, MessageCollector.Collect)
//
//}
//
