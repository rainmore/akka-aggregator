package net.rainmore.generators

import net.rainmore._
import org.jfairy.Fairy

import scala.collection.mutable.ListBuffer
import scala.util.Random

object Common {
    val fairy = Fairy.create()

    val tenants = Set("a", "b", "c", "d").foldLeft(Map.empty[String, List[Id]]){(current, tenant) =>
        current + (tenant -> userIds(tenant))
    }

    private def userIds(tenant: String) = {
        val r = Random.shuffle((1 to 10).toSet).head
        (0 to r).foldLeft(new ListBuffer[Id]()) {(list, i) =>
            list += new Id(tenant, fairy.textProducer().word(1))
        }.toList
    }
}

object SqsGenerator {
    import  Common._

    private val sqsSize = 10
    private val titleLength = 2
    private val bodyLength = 10
    private val certificateLength = 128

    def generate: List[Sqs] = {
        Random.shuffle(Common.tenants).foldLeft(ListBuffer[Sqs]()){ case (buffer, (tenant, ids)) =>
            buffer ++= SqsGenerator.generate(ids).to[ListBuffer]
        }.toList
    }


    def generate(ids: List[Id]): List[Sqs] = {
        ids.foldLeft(ListBuffer[Sqs]()){ (list, id) =>
            list ++= generateList(id, Option.empty)
        }.toList
    }

    def generateList(id: Id, num: Option[Int] = Option(1)): List[Sqs] = {
        val n = if (num.isEmpty) (Random.nextInt(sqsSize) + 2)
        else Math.abs(num.get)

        if (n <= 1) List(generateOne(id))
        else {
            (1 to n).foldLeft(ListBuffer[Sqs]()){(list, n) =>
                list += generateOne(id)
            }.toList
        }
    }

    def generateOne(id: Id): Sqs = {
        val title: String = fairy.textProducer().word(titleLength)
        val body: String = fairy.textProducer().word(bodyLength)
        val device: Device.Value = Random.shuffle(Device.values.toSet).head
        new Sqs(id, title, body, Set(generateCertificate(device)), Set(generateToken(device)))
    }

    def generateCertificate(device: Device.Value): Certificate = {
        new Certificate(device, Common.fairy.textProducer().randomString(certificateLength))
    }

    def generateToken(device: Device.Value): Token = {
        val c = if (Device.android.eq(device)) Common.fairy.textProducer().randomString(32)
        else Common.fairy.textProducer().randomString(64)
        new Token(device, c)
    }

    def generateResult(messages: List[Sqs]): Map[Id, ListBuffer[Notification]] = {
        messages.foldLeft(Map[Id, ListBuffer[Notification]]()){(map, sqs) =>
            map + (sqs.id -> (map.getOrElse(sqs.id, ListBuffer[Notification]()) += sqs.toNotification))
        }
    }
}
