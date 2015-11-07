package net.rainmore.hedwig

import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import net.rainmore.cluster.JobReceptionist.JobRequest
import net.rainmore.cluster.JobReceptionist

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object Main extends App with LazyLogging {

    val config = ConfigFactory.load()
    val name = "clusters"

    val system = ActorSystem(name, config)

    // register cluster manager
    system.actorOf(ClusterManager.props, ClusterManager.name)

    Cluster(system).registerOnMemberRemoved {

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
//
//    Cluster(system).registerOnMemberUp {
//        import scala.concurrent.ExecutionContext.Implicits.global
//        logger.info("Start member")
//
//        val name = JobReceptionist.name
//
//        //TODO to check if there were more than one
//        system.actorSelection(s"/user/$name").resolveOne(5.seconds).onComplete {
//            case Success(actor) => {
//                logger.info("Master node exists")
//            }
//            case Failure(ex) => initJobReceptionist()
//        }
//    }
//
//    private def initJobReceptionist(): Unit = {
//        val name = classOf[JobReceptionist].getSimpleName
//
//        val receptionist = system.actorOf(Props[JobReceptionist], name)
//        logger.info("Master node is ready.{}", receptionist)
//
//        val text = List("this is a test", "of some very naive word counting", "but what can you say", "it is what it is")
//        receptionist ! JobRequest("the first job", (1 to 100000).flatMap(i => text ++ text).toList)
//
//        // onComplete http://stackoverflow.com/questions/26541784/how-select-akka-actor-with-actorselection?answertab=votes#tab-top
//        // people said it will on different thread
//        // just comment it out and maybe use later
////        system.actorSelection(name).resolveOne(5.seconds).onComplete {
//    }
}