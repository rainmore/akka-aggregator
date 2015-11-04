package net.rainmore.aggregator

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit}
import com.typesafe.config.ConfigFactory
import net.rainmore.aggregator.JobWorker.Work
import net.rainmore.generators.{SqsGenerator, Common}
import net.rainmore.{Notification, Sqs, Id, UnitSpec}

import scala.collection.mutable.ListBuffer
import scala.util.Random

class JobWorkerSpec1 extends TestKit(ActorSystem("JobWorkerSpec", ConfigFactory.load("workerTest.conf")))
    with UnitSpec {

    describe("lalla") {
        it ("should work") {
            val ids: List[Id] = Random.shuffle(Common.tenants).head._2
            val sqsMessages: List[Sqs] = SqsGenerator.generate(ids)

            val workerRef= TestActorRef[JobWorker]
            val masterRef = TestActorRef[JobMaster]
            //                worker ! Task(sqsMessages, master)

            val notifications: Map[Id, ListBuffer[Notification]] = workerRef.underlyingActor.processTask(sqsMessages)

            notifications.foreach( item => {
                val count = sqsMessages.filter(sqs => sqs.id.tenant.eq(item._1.tenant) && sqs.id.user.eq(item._1.user)).size
                count should equal(item._2.size)
            })

            workerRef ! Work(masterRef)
            expectMsg()
        }
    }

}
