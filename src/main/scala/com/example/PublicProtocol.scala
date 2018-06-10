package com.example.shared

object PublicProtocol {
  sealed trait Message
  case class login(username: String, password: String) extends Message
  object login_failed extends Message
  case class login_successful(user_type : String) extends Message
  case class ping(seq : Int) extends Message
  case class pong(seq: Int) extends Message
  case class failure(message: String) extends Message
}