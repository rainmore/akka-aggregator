package net.rainmore

import com.typesafe.config.ConfigFactory
import akka.actor.{Props, ActorSystem}
import akka.cluster.Cluster

import com.typesafe.scalalogging.LazyLogging
import net.rainmore.cluster.{ClusterListener, JobReceptionist}
import net.rainmore.cluster.JobReceptionist.JobRequest

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.Try

object Main extends App with LazyLogging {
    val config = ConfigFactory.load()

    val system = ActorSystem("clusters", config)
    val cluster = Cluster(system)

    logger.info(s"Starting node with roles: ${cluster.selfRoles}")

    // register the node listener
    system.actorOf(Props(new ClusterListener), "cluster-listener")

    if(system.settings.config.getStringList("akka.cluster.roles").contains("master")) {
        cluster.registerOnMemberUp {
            val receptionist = system.actorOf(Props[JobReceptionist], "receptionist")
            println("Master node is ready.")

            val text = List("this is a test", "of some very naive word counting", "but what can you say", "it is what it is")
            receptionist ! JobRequest("the first job", (1 to 100000).flatMap(i => text ++ text).toList)
        }
    }

    cluster.registerOnMemberRemoved {

        logger.info("------------ Node is leaving")

        // exit JVM when ActorSystem has been terminated
        system.registerOnTermination(System.exit(0))
        // shut down ActorSystem
        system.terminate()

        // In case ActorSystem shutdown takes longer than 10 seconds,
        // exit the JVM forcefully anyway.
        // We must spawn a separate thread to not block current thread,
        // since that would have blocked the shutdown of the ActorSystem.
        new Thread {
            override def run(): Unit = {
                if (Try(Await.ready(system.whenTerminated, 10.seconds)).isFailure)
                    System.exit(-1)
            }
        }.start()
    }

}