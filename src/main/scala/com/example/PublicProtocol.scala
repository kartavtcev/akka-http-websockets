package com.example.shared

object PublicProtocol {
  sealed trait Message
  case class TextMessage(message: String) extends Message // in
  case class TextMessageWithSender(id: String, message: String) extends Message // out
  case class Joined(id: String) extends Message // out
  case class Left(id: String) extends Message // out
}

object Roles {
  sealed trait Role
  object Admin extends Role
  object User extends Role
}