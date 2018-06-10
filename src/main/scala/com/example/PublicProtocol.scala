package com.example.shared

// TODO: use generic derivation
/*
https://stackoverflow.com/questions/42165460/how-to-decode-an-adt-with-circe-without-disambiguating-objects
*/

object PublicProtocol {
  sealed trait Message
  /*
  sealed trait Typed extends Message{
    val $type : String
  }
  */
  case class login(username: String, password: String) extends Message
  case class TextMessage($type : String, message: String) extends Message
  //case class TextMessageWithSender($type : String, message: String) extends Typed
  //case class Joined(id: String) extends Message
  //case class Left(id: String) extends Message
  object login_failed extends Message
  case class login_successful(user_type : String) extends Message
  case class ping(seq : Int) extends Message
  case class pong(seq: Int) extends Message
  //case class Heartbeat($type : String, seq : Int) extends Typed
}