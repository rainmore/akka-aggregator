package net.rainmore

import scala.collection.mutable.{ListBuffer, ArrayBuffer}

object DeviceType extends Enumeration {
    val ios, android = Value
}

case class Recipient(tenant: String, user: String) {
    override def toString() = "%s-%s".format(tenant, user)
}

case class Certificate(deviceType: DeviceType.Value, certificate: String)

case class Token (deviceType: DeviceType.Value, token: String)

// TODO to confirm the document of message
case class Message(id: Int, title: String, body: String)

object Notification {
    def apply (tenantId: String, userId: String, message: Message, certificates: Map[DeviceType.Value, String],  tokens: Map[DeviceType.Value, String]) = {
        val id = new Recipient(tenantId, userId)
        val certs = certificates.foldLeft(ListBuffer[Certificate]()){(list, item) => list += new Certificate(item._1, item._2)}
        val ts = certificates.foldLeft(ListBuffer[Token]()){(list, item) => list += new Token(item._1, item._2)}
        new Notification(new Recipient(tenantId, userId), message, certs.toSet, ts.toSet)
    }
}

case class Notification(recipient: Recipient, message: Message, certificates: Set[Certificate], tokens: Set[Token])


object PushMessage {
    // TODO use phrase here
    private val bodyTemplateForMessageWithSameTitle = "There are %s new message(s)"
    private val titleTemplateForMessageWithMultiTitle = "%s new message(s)"


    def apply(message: Message): PushMessage = new PushMessage(message.title, message.body, Set[Int](message.id))

    def apply(messages: List[Message]): PushMessage = {
        require(messages.size > 0)

        if (messages.size == 1) apply(messages(0))
        else {
            val map = messages.foldLeft(Map.empty[String, ListBuffer[Message]]){(map, item) =>
                // TODO to remove `map +`
                map + (item.title -> (map.getOrElse(item.title, ListBuffer[Message]()) += item))
            }
            if (map.size == 1) {
                new PushMessage(
                    map.head._1,
                    bodyTemplateForMessageWithSameTitle.format(map.head._2.size),
                    map.head._2.map(_.id).toSet)
            }
            else {
                new PushMessage(
                    titleTemplateForMessageWithMultiTitle.format(messages.size),
                    map.map(item => item._1).mkString(", "), //TODO to trim the size if it is too big
                    messages.map(_.id).toSet
                )
            }
        }

    }
}

case class PushMessage(title: String, body: String, ids: Set[Int])

