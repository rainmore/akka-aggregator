package net.rainmore.aggregator

import akka.testkit.TestActorRef
import net.rainmore.UnitSpec

class JobWorkerSpec extends UnitSpec {

    describe("An aggregator job worker") {
        describe("when receive a message") {
            it ("should change stat") {

                val silentActor = TestActorRef[JobWorker]
            }
        }
    }

}
