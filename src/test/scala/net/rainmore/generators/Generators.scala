package net.rainmore.generators

import net.rainmore.{Token, Device, Certificate, Sqs, Id}
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

    def generate(ids: List[Id]): List[Sqs] = {
        ids.foldLeft(ListBuffer[Sqs]()){ (list, id) =>
            list ++= generateList(id, Option.empty)
        }.toList
    }

    def generateList(id: Id, num: Option[Int] = Option(1)): List[Sqs] = {
        val n = if (num.isEmpty) (Random.nextInt(10) + 2)
        else Math.abs(num.get)

        if (n <= 1) List(generate(id))
        else {
            (1 to n).foldLeft(ListBuffer[Sqs]()){(list, n) =>
                list += generate(id)
            }.toList
        }
    }

    def generate(id: Id): Sqs = {
        val title: String = fairy.textProducer().sentence()
        val body: String = fairy.textProducer().loremIpsum()
        val device: Device.Value = Random.shuffle(Device.values.toSet).head
        new Sqs(id, title, body, Set(generateCertificate(device)), Set(generateToken(device)))
    }

    def generateCertificate(device: Device.Value): Certificate = {
        new Certificate(device, Common.fairy.textProducer().randomString(128))
    }

    def generateToken(device: Device.Value): Token = {
        val c = if (Device.android.eq(device)) Common.fairy.textProducer().randomString(32)
        else Common.fairy.textProducer().randomString(64)
        new Token(device, c)
    }
}
