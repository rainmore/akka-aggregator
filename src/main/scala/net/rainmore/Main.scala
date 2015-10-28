package net.rainmore

import com.typesafe.config.ConfigFactory
import akka.actor.{Props, ActorSystem}
import akka.cluster.Cluster

import com.typesafe.scalalogging.LazyLogging
import net.rainmore.cluster.JobReceptionist
import net.rainmore.cluster.JobReceptionist.JobRequest

object Main extends App with LazyLogging {
    val config = ConfigFactory.load("app.conf")

    val system = ActorSystem("clusters", config)

    logger.info(s"Starting node with roles: ${Cluster(system).selfRoles}")

//    if(system.settings.config.getStringList("akka.cluster.roles").contains("master")) {
//        Cluster(system).registerOnMemberUp {
//            val receptionist = system.actorOf(Props[JobReceptionist], "receptionist")
//            println("Master node is ready.")
//
//            val text = List("this is a test", "of some very naive word counting", "but what can you say", "it is what it is")
//            receptionist ! JobRequest("the first job", (1 to 100000).flatMap(i => text ++ text).toList)
//            system.actorOf(Props(new ClusterDomainEventListener), "cluster-listener")
//        }
//    }
}