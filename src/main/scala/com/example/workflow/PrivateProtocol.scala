package com.example.workflow

import akka.actor.ActorRef

object PrivateProtocol {
  sealed trait Event
  case class Joined(ref: ActorRef, role: Roles.Role) extends Event
  case object Left extends Event
  /*
  case class ReceivedMessage(message: String) extends Event {
    def toMessage: PublicProtocol.TextMessageWithSender = PublicProtocol.TextMessageWithSender(message)
  }
  */
  case class Role(role : Roles.Role) extends Event
}

object Roles {
  sealed trait Role
  object Admin extends Role
  object User extends Role
  object Unknown extends Role
}