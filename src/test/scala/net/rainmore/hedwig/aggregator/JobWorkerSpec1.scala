package net.rainmore.hedwig.aggregator

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit}
import com.typesafe.config.ConfigFactory
import net.rainmore.hedwig._
import net.rainmore.hedwig.aggregator.JobWorker.Work

import scala.collection.mutable.ListBuffer
import scala.util.Random

class JobWorkerSpec1 extends TestKit(ActorSystem("JobWorkerSpec", ConfigFactory.load("workerTest.conf")))
    with UnitSpec {

    describe("lalla") {
        it ("should work") {
            val ids: List[Recipient] = Random.shuffle(Common.tenants).head._2
            val sqsMessages: List[Notification] = SqsGenerator.generate(ids)

            val workerRef= TestActorRef[JobWorker]
            val masterRef = TestActorRef[JobMaster]
            //                worker ! Task(sqsMessages, master)

            val notifications: Map[Recipient, ListBuffer[Notification]] = workerRef.underlyingActor.processTask(sqsMessages)

            notifications.foreach( item => {
                val count = sqsMessages.filter(sqs => sqs.recipient.tenant.eq(item._1.tenant) && sqs.recipient.user.eq(item._1.user)).size
                count should equal(item._2.size)
            })

            workerRef ! Work(masterRef)
            expectMsg(notifications)
        }
    }

}
