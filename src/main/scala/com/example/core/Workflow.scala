package com.example.core

import akka.actor._
import akka.event.{Logging, LoggingAdapter}
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import akka.util.Timeout
import com.example.core.PrivateProtocol._
import com.example.shared.PublicProtocol

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._



trait Workflow {
  def flow: Flow[PublicProtocol.Message, PublicProtocol.Message, Any]
}

object Workflow {
  def uuid = java.util.UUID.randomUUID.toString

  def create(system: ActorSystem)(implicit ec: ExecutionContext): Workflow = {

    lazy val log = Logging(system, classOf[Workflow])
    val authActor = system.actorOf(AuthActor.props(log), "authActor")
    val tableManagerActor = system.actorOf(TableManagerActor.props(log), "tableManagerActor")

    // TODO: flow is served by single actor, i.e. similar to single thread. => Workflow actor must not stuck/lock/wait.
    val workflowActor = system.actorOf(WorkflowActor.props(log, authActor, tableManagerActor), "workflowActor")

    new Workflow {
      def flow: Flow[PublicProtocol.Message, PublicProtocol.Message, Any] = {
        val connectId = uuid

        // sink -> UserLeft -> workflow actor; + ReceivedMessage's In
        val in =
          Flow[PublicProtocol.Message]
            .collect { case message : PublicProtocol.Message =>  IdWithInMessage(connectId, message) }
            .to(Sink.actorRef[Event](workflowActor, Left(connectId)))

        // source -> materialized actor -> NewUser + ActorRef -> workflow actor; + PublicProtocol.Message's Out
        // buffer 1 message, then fail; actor Tell ! strategy
        val out =
          Source.actorRef[PublicProtocol.Message](1, OverflowStrategy.fail)
            .mapMaterializedValue(workflowActor ! Joined(connectId, _))

        Flow.fromSinkAndSource(in, out)
      }
    }
  }
}

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
          actorRefById(connectId) ! PublicProtocol.fail

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

