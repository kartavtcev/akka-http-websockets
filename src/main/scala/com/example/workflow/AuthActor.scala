package com.example.workflow

import akka.actor.{Actor, Props}
import akka.event.LoggingAdapter
import com.example.shared.PublicProtocol

object AuthActor {
  def props(log: LoggingAdapter): Props = Props(classOf[AuthActor], log)
  def isAdmin(role : Roles.Role): Unit = {
    role match {
      case Roles.Admin => return true
      case _ => return false
    }
  }
  def isAuthed(role : Roles.Role): Unit = {
    role match {
      case Roles.Admin => return true
      case Roles.User => return true
      case _ => return false
    }
  }
}

class AuthActor(val log: LoggingAdapter) extends Actor {
  var adminsCredentials = Map[String, String]("admin" -> "admin")
  var userCredentials = Map[String, String] ("user1234" -> "password1234")

  override def receive: Receive = {
    case PublicProtocol.Auth(_, username, password) =>
      val admin = adminsCredentials.find(_ == (username, password))
      lazy val user = userCredentials.find(_ == (username, password))
      // TODO: use Monad.Map2 monadic combinator
      admin match {
        case Some(_) => sender() ! PrivateProtocol.Role(Roles.Admin)
        case None =>
          user match {
            case Some(_) => sender() ! PrivateProtocol.Role(Roles.User)
            case None => sender() ! PrivateProtocol.Role(Roles.Unknown)
          }
      }
  }
}