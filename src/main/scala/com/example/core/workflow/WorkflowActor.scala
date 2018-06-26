package com.example.core.workflow

import akka.actor._
import akka.event.LoggingAdapter
import akka.pattern.ask
import akka.util.Timeout
import com.example.core.PrivateProtocol._
import com.example.core._
import com.example.shared.PublicProtocol

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object WorkflowActor {
  def props(log: LoggingAdapter, authActor: ActorRef, tableManagerActor: ActorRef)
           (implicit ec: ExecutionContext): Props =
    Props(new WorkflowActor(log, authActor, tableManagerActor))
}

class WorkflowActor(val log: LoggingAdapter, val authActor : ActorRef, val tableManagerActor : ActorRef)
                   (implicit ec: ExecutionContext) extends Actor {
  var connected = Map.empty[String, (Option[String], ActorRef)] // store only username if user is authenticated + authorized
  implicit val timeout: Timeout = Timeout(1 seconds)

  def actorRefById(connectId: String): ActorRef = connected.get(connectId).get._2

  def usernameById(connectId: String): Option[String] = connected.get(connectId).get._1

  def isAlreadyAuthorizedFromDifferentMachine(currentConnectId: String, currentUsername: String) = {
    val opt = connected
      .find {
        case (connectId, (username, _)) => username == Some(currentUsername) && connectId != currentConnectId
      }
    !opt.isEmpty
  }

  def replaceValueByKey(key: String, username: Option[String], ref: ActorRef): Unit = {
    connected -= key
    connected += (key -> (username, ref))
  }

  override def receive: Receive = {
    case IdWithInMessage(connectId, message) =>
      message match {

        case auth: PublicProtocol.login => {

          log.info(s"auth: ${connectId}, ${auth.username}")

          (authActor ? auth) map {
            case
              Role(role) =>
              if (isAlreadyAuthorizedFromDifferentMachine(connectId, auth.username)) {
                actorRefById(connectId) ! PublicProtocol.fail("Already authorized from different machine")
              } else {
                val ref = actorRefById(connectId)

                role match {
                  case Some(Roles.Admin) =>
                    replaceValueByKey(connectId, Some(auth.username), ref)
                    ref ! PublicProtocol.login_successful(user_type = "admin")

                  case Some(Roles.User) =>
                    replaceValueByKey(connectId, Some(auth.username), ref)
                    ref ! PublicProtocol.login_successful(user_type = "user")

                  case None => ref ! PublicProtocol.login_failed
                }
              }
          }
        }

        case PublicProtocol.subscribe_tables =>
          usernameById(connectId) match {
            case None => actorRefById(connectId) ! PublicProtocol.not_authorized
            case Some(name) =>
              (authActor ? PrivateProtocol.RoleByNameRequest(name)).map {
                case Role(role) =>
                  if (AuthActor.isAuthed(role)) {
                    (tableManagerActor ? IdWithInMessage(name, message)).map {
                      case TablesEvent(tables) =>
                        actorRefById(connectId) ! PublicProtocol.table_list(tables.toList)
                    }
                  } else {
                    actorRefById(connectId) ! PublicProtocol.not_authorized
                  }
              } // .recover{ case err => log.error(err.toString) } // will be handled by Route.seal, bubble-up exceptions
          }

        case PublicProtocol.unsubscribe_tables =>
          usernameById(connectId) match {
            case None => actorRefById(connectId) ! PublicProtocol.not_authorized
            case Some(name) =>
              (authActor ? PrivateProtocol.RoleByNameRequest(name))
                .map {
                  case Role(role) =>
                    if (AuthActor.isAuthed(role)) {
                      tableManagerActor ! IdWithInMessage(name, message)
                    } else {
                      actorRefById(connectId) ! PublicProtocol.not_authorized
                    }
                }
          }

        case PublicProtocol.add_table(_, _) |
             PublicProtocol.update_table(_) |
             PublicProtocol.remove_table(_) =>

          usernameById(connectId) match {
            case None => actorRefById(connectId) ! PublicProtocol.not_authorized
            case Some(name) =>
              (authActor ? PrivateProtocol.RoleByNameRequest(name))
                .map {
                  case Role(role) =>
                    if (AuthActor.isAdmin(role)) {
                      (tableManagerActor ? message).map {
                        case TableEvent(event, subscribers) =>
                          subscribers.foreach { s =>
                            connected
                              .find {
                                case (_, (username, _)) => username == Some(s)
                              }
                              .foreach {
                                case (_, (_, ref)) => ref ! event
                              }
                          }
                      }
                    } else {
                      actorRefById(connectId) ! PublicProtocol.not_authorized
                    }
                }
          }

        case PublicProtocol.ping(seq) =>
          actorRefById(connectId) ! PublicProtocol.pong(seq)

        case msg: PublicProtocol.fail =>
          actorRefById(connectId) ! msg

        case unmatched =>
          log.error(s"Unmatched message: ${unmatched.toString}")
          actorRefById(connectId) ! PublicProtocol.fail("Error happened. Sorry :(")
      }

    case Joined(connectId, ref) =>
      log.info(s"new user: $connectId")
      connected += (connectId -> (None, ref))

    case Left(connectId) =>
      log.info(s"user left: $connectId")
      connected = connected.filterNot(_._1 == connectId)

    case unmatched =>
      log.error(s"Unmatched message: ${unmatched.toString}")
  }
}