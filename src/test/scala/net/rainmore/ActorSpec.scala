package net.rainmore

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, DefaultTimeout, TestKit}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest._

abstract class ActorSpec extends TestKit(ActorSystem("testsystem"))
with UnitSpec {
}

trait UnitSpec extends FunSpecLike with ShouldMatchers
with BeforeAndAfterAll with DefaultTimeout with ImplicitSender
with GivenWhenThen
with LazyLogging { this: TestKit with Suite =>
    override protected def afterAll() {
        super.afterAll()
        system.terminate()
    }
}
