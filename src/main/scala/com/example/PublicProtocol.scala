package com.example.shared

// TODO: use generic derivation
/*
https://stackoverflow.com/questions/42165460/how-to-decode-an-adt-with-circe-without-disambiguating-objects
*/

object PublicProtocol {
  sealed trait Message
  sealed trait Typed extends Message{
    val $type : String
  }
  case class Auth($type : String = "login", username: String, password: String) extends Typed
  case class TextMessage($type : String, message: String) extends Typed
  //case class TextMessageWithSender($type : String, message: String) extends Typed
  //case class Joined(id: String) extends Message
  //case class Left(id: String) extends Message
  case class LoginFailed($type : String = "login_failed") extends Typed
  case class LoginSuccess($type : String = "login_successful", user_type : String) extends Typed
  case class Heartbeat($type : String, seq : Int) extends Typed
}