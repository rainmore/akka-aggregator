package net.rainmore

object Device extends Enumeration {
    val ios, android = Value
}

case class Id(tenant: String, user: String) {
    override def toString() = "%s-%s".format(tenant, user)
}
case class Certificate(device: Device.Value, certificate: String)
case class Token (device: Device.Value, token: String)

trait Message {
    val title: String
    val body: String
    val certificates: Set[Certificate]
    val tokens: Set[Token]
}
case class Notification(title: String, body: String, certificates: Set[Certificate], tokens: Set[Token]) extends Message
case class Sqs(id: Id, title: String, body: String, certificates: Set[Certificate],  tokens: Set[Token]) extends Message {
    def toNotification = new Notification(title, body, certificates, tokens)
}
