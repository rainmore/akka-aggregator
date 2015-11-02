package net.rainmore

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{GivenWhenThen, FunSpecLike, ShouldMatchers}

abstract class UnitSpec extends TestKit(ActorSystem("testsystem"))
with FunSpecLike with ShouldMatchers
with StopSystemAfterAll
with GivenWhenThen
with LazyLogging {

}
