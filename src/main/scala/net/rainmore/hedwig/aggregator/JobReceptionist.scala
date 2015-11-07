package net.rainmore.hedwig.aggregator

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props, SupervisorStrategy, Terminated}
import net.rainmore.hedwig.{Notification, Recipient}

import scala.collection.mutable.ListBuffer


object JobReceptionist {
    def props = Props(new JobReceptionist)
    def name = "receptionist"

    case class JobRequest(name: String, messages: List[Notification])

    sealed trait Response
    case class JobSuccess(name: String, map: Map[Recipient, ListBuffer[Notification]]) extends Response
    case class JobFailure(name: String) extends Response

    case class Aggregate(name: String, map: Map[Recipient, ListBuffer[Notification]])

    case class Job(name: String, messages: List[Notification], respondTo: ActorRef, jobMaster: ActorRef)
}

class JobReceptionist extends Actor with ActorLogging with CreateMaster {
    import JobReceptionist._
    import context._

    override def supervisorStrategy: SupervisorStrategy =
        SupervisorStrategy.stoppingStrategy

    var jobs = Set[Job]()
    var retries = Map[String, Int]()
    val maxRetries = 3


    def receive = {
        case jr @ JobRequest(name, messages) =>
            log.info(s"Received job $name")

            val masterName = "master-%s".format(URLEncoder.encode(name, StandardCharsets.UTF_8.toString))
            val jobMaster = createMaster(masterName)

            val job = Job(name, messages, sender, jobMaster)
            jobs = jobs + job

            jobMaster ! JobMaster.StartJob(name, messages)
            watch(jobMaster)

        case Aggregate(jobName, map) =>
            log.info(s"Job $jobName complete.")
            log.info(s"result:${map}")
            jobs.find(_.name == jobName).foreach { job =>
                job.respondTo ! JobSuccess(jobName, map)
                stop(job.jobMaster)
                jobs = jobs - job
            }

        case Terminated(jobMaster) =>
            jobs.find(_.jobMaster == jobMaster).foreach { failedJob =>
                log.error(s"Job Master $jobMaster terminated before finishing job.")

                val name = failedJob.name
                log.error(s"Job ${name} failed.")
                val nrOfRetries = retries.getOrElse(name, 0)

                if(maxRetries > nrOfRetries) {
                    if(nrOfRetries == maxRetries -1) {
                        // Simulating that the Job worker will work just before max retries
                        self.tell(JobRequest(name, failedJob.messages), failedJob.respondTo)
                    } else self.tell(JobRequest(name, failedJob.messages), failedJob.respondTo)

                    retries = retries + retries.get(name).map(r=> name -> (r + 1)).getOrElse(name -> 1)
                }
            }
    }
}

trait CreateMaster {
    def context: ActorContext
    def createMaster(name: String) = context.actorOf(JobMaster.props, name)
}