package net.rainmore.aggregator

import akka.actor.{Actor, ActorContext, ActorRef, Props}
import akka.routing.BroadcastPool
import akka.testkit.{ImplicitSender, TestActorRef}
import net.rainmore.{Notification1, Recipient, Notification$, ActorSpec}
import net.rainmore.aggregator.JobReceptionist.{JobSuccess, JobRequest}
import net.rainmore.generators.{Common, SqsGenerator}

import scala.collection.mutable.ListBuffer
import scala.util.Random

class JobReceptionistSpec extends ActorSpec with ImplicitSender {

//    val jobReceptionList = system.actorOf(Props[JobReceptionist], JobReceptionist.name)
    val jobReceptionList = TestActorRef(Props[JobReceptionist], JobReceptionist.name)

    describe("JobReceptionList") {
        it ("should work") {
            val name = "test1"
            val sqsMessages = SqsGenerator.generate(Random.shuffle(Common.tenants).head._2)


            val result = generateResult(sqsMessages)
            logger.info("sqsMessage size: %s".format(sqsMessages.size))
            result.foreach {case (id, list) => logger.info("result: id: %s, size: %s".format(id, list.size))}

            jobReceptionList ! JobRequest(name, sqsMessages)
            expectMsg(JobSuccess(name, result))
            expectNoMsg()
        }
    }

    private def generateResult(messages: List[Notification]): Map[Recipient, ListBuffer[Notification1]] = {
        messages.foldLeft(Map[Recipient, ListBuffer[Notification1]]()){(map, sqs) =>
            map + (sqs.id -> (map.getOrElse(sqs.id, ListBuffer[Notification1]()) += sqs.toNotification))
        }
    }
}




trait CreateLocalWorkerRouter extends CreateWorkerRouter {  this: Actor =>
    def context: ActorContext

    override def createWorkerRouter: ActorRef = {
        context.actorOf(BroadcastPool(5).props(Props[JobWorker]), "worker-router")
    }
}

class TestJobMaster extends JobMaster with CreateLocalWorkerRouter

class TestReceptionist extends JobReceptionist with CreateMaster {
    override def createMaster(name: String): ActorRef = {
        context.actorOf(Props[TestJobMaster], name)
    }
}