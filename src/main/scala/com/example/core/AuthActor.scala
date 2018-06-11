package com.example.core

import akka.actor.{Actor, Props}
import akka.event.LoggingAdapter
import com.example.shared.PublicProtocol

object AuthActor {
  def props(log: LoggingAdapter): Props = Props(classOf[AuthActor], log)
  def isAdmin(role : Option[Roles.Role]): Unit = {
    role match {
      case Some(Roles.Admin) => return true
      case _ => return false
    }
  }
  def isAuthed(role : Option[Roles.Role]): Unit = {
    role match {
      case Some(Roles.Admin) | Some(Roles.User) => return true
      case _ => return false
    }
  }
}

class AuthActor(val log: LoggingAdapter) extends Actor {
  var imitateDBUserNamePassword = Map[String, String] ("admin" -> "admin", "user1234" -> "password1234") // NOT TODO: do not repeat this at home. Use hash + salt for PWD storage.
  var imitateDBUserRole = Map[String, Roles.Role] ("admin" -> Roles.Admin, "user1234" -> Roles.User)
  // ^^ check out my Slick + H2 in-memory DB code sample @ https://github.com/kartavtcev/records

  override def receive: Receive = {
    case PublicProtocol.login(username, password) =>
      val r =
        imitateDBUserNamePassword
          .find(_ == (username, password))
          .flatMap{ case (name, _) => imitateDBUserRole.get(name) }
      sender() ! PrivateProtocol.Role(r)
  }
}