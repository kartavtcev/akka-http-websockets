package com.example.workflow

import akka.actor.ActorRef
import com.example.shared.PublicProtocol

object PrivateProtocol {
  sealed trait Event
  case class Joined(connectId: String, ref: ActorRef) extends Event
  case class Left(connectId: String) extends Event

  case class Role(role : Option[Roles.Role]) extends Event
  case class IdWithInMessage(connectId: String, message: PublicProtocol.Message) extends Event
}

object Roles {
  sealed trait Role
  object Admin extends Role
  object User extends Role
}