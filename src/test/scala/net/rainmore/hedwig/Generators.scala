package net.rainmore.hedwig

import org.jfairy.Fairy

import scala.collection.mutable.ListBuffer
import scala.util.Random

object Common {
    val fairy = Fairy.create()

    val tenants = Set("a", "b", "c", "d").foldLeft(Map.empty[String, List[Recipient]]){(current, tenant) =>
        current + (tenant -> userIds(tenant))
    }

    private def userIds(tenant: String) = {
        val r = Random.shuffle((1 to 10).toSet).head
        (0 to r).foldLeft(new ListBuffer[Recipient]()) {(list, i) =>
            list += new Recipient(tenant, fairy.textProducer().word(1))
        }.toList
    }
}

object SqsGenerator {
    import Common._

    private val sqsSize = 10
    private val titleLength = 2
    private val bodyLength = 10
    private val certificateLength = 128

    def generate: List[Notification] = {
        Random.shuffle(Common.tenants).foldLeft(ListBuffer[Notification]()){ case (buffer, (tenant, ids)) =>
            buffer ++= SqsGenerator.generate(ids).to[ListBuffer]
        }.toList
    }


    def generate(ids: List[Recipient]): List[Notification] = {
        ids.foldLeft(ListBuffer[Notification]()){ (list, id) =>
            list ++= generateList(id, Option.empty)
        }.toList
    }

    def generateList(id: Recipient, num: Option[Int] = Option(1)): List[Notification] = {
        val n = if (num.isEmpty) (Random.nextInt(sqsSize) + 2)
        else Math.abs(num.get)

        if (n <= 1) List(generateOne(id))
        else {
            (1 to n).foldLeft(ListBuffer[Notification]()){(list, n) =>
                list += generateOne(id)
            }.toList
        }
    }

    def generateOne(recipient: Recipient): Notification = {
        val id: Int = Random.nextInt(1000000000)
        val title: String = fairy.textProducer().word(titleLength)
        val body: String = fairy.textProducer().word(bodyLength)
        val device: DeviceType.Value = Random.shuffle(DeviceType.values.toSet).head
        new Notification(recipient, new Message(id, title, body), Set(generateCertificate(device)), Set(generateToken(device)))
    }

    def generateCertificate(device: DeviceType.Value): Certificate = {
        new Certificate(device, Common.fairy.textProducer().randomString(certificateLength))
    }

    def generateToken(device: DeviceType.Value): Token = {
        val c = if (DeviceType.android.eq(device)) Common.fairy.textProducer().randomString(32)
        else Common.fairy.textProducer().randomString(64)
        new Token(device, c)
    }

    def generateResult(messages: List[Notification]): Map[Recipient, ListBuffer[Notification]] = {
        val map = Map[Recipient, ListBuffer[Notification]]()
        messages.foreach(n => map +  (n.recipient -> (map.getOrElse(n.recipient, ListBuffer[Notification]()) += n)))
        map
    }
}
