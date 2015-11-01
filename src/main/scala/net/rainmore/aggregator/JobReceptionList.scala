package net.rainmore.aggregator

import java.net.URLEncoder

import akka.actor.{ActorContext, Terminated, SupervisorStrategy, ActorLogging, Actor, ActorRef, Props}
import net.rainmore.cluster.JobReceptionist.WordCount
import net.rainmore.{Sqs, Message, Id}


object JobReceptionist {
    def props = Props(new JobReceptionist)
    def name = "receptionist"

    case class JobRequest(name: String, messages: Set[Sqs])

    sealed trait Response
    case class JobSuccess(name: String, map: Map[Id, Set[Message]]) extends Response
    case class JobFailure(name: String) extends Response

    case class Aggregate(name: String, map: Map[Id, Set[Message]])

    case class Job(name: String, messages: Set[Sqs], respondTo: ActorRef, jobMaster: ActorRef)
}

class JobReceptionist extends Actor with ActorLogging with CreateMaster {
    import JobReceptionist._
    import JobMaster.StartJob
    import context._

    override def supervisorStrategy: SupervisorStrategy =
        SupervisorStrategy.stoppingStrategy

    var jobs = Set[Job]()
    var retries = Map[String, Int]()
    val maxRetries = 3


    def receive = {
        case jr @ JobRequest(name, messages) =>
            log.info(s"Received job $name")

            val masterName = "master-"+URLEncoder.encode(name, "UTF8")
            val jobMaster = createMaster(masterName)

            val job = Job(name, messages, sender, jobMaster)
            jobs = jobs + job

            jobMaster ! StartJob(name, messages)
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
                        val text = failedJob.text.filterNot(_.contains("FAIL"))
                        self.tell(JobRequest(name, text), failedJob.respondTo)
                    } else self.tell(JobRequest(name, failedJob.text), failedJob.respondTo)

                    retries = retries + retries.get(name).map(r=> name -> (r + 1)).getOrElse(name -> 1)
                }
            }
    }
}

trait CreateMaster {
    def context: ActorContext
    def createMaster(name: String) = context.actorOf(JobMaster.props, name)
}