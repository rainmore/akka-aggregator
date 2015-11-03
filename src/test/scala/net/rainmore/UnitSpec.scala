package net.rainmore

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, FunSpecLike, ShouldMatchers}

abstract class UnitSpec extends TestKit(ActorSystem("testsystem"))
with FunSpecLike with ShouldMatchers
with BeforeAndAfterAll
with GivenWhenThen
with LazyLogging {

    override protected def afterAll() {
        super.afterAll()
        system.terminate()
    }
}
