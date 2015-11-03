package net.rainmore.aggregator

import akka.testkit.TestActorRef
import net.rainmore.aggregator.JobWorker.Task
import net.rainmore.{Notification, Sqs, Id, UnitSpec}
import net.rainmore.generators.{SqsGenerator, Common}

import scala.collection.mutable.ListBuffer
import scala.util.Random

class JobWorkerSpec extends UnitSpec {



    describe("JobWorker") {
        describe("when receive a `Task` message") {
            it ("should process sqs messages") {
                val ids: List[Id] = Random.shuffle(Common.tenants).head._2
                val sqsMessages: List[Sqs] = SqsGenerator.generate(ids)

                val worker = TestActorRef[JobWorker]
                val master = TestActorRef[JobMaster]
                worker ! Task(sqsMessages, master)

                val notifications: Map[Id, ListBuffer[Notification]] = worker.underlyingActor.processTask(sqsMessages)

                notifications.foreach( item => {
                    val count = sqsMessages.filter(sqs => sqs.id.tenant.eq(item._1.tenant) && sqs.id.user.eq(item._1.user)).size
                    count should equal(item._2.size)
                })
            }
        }
    }

}
