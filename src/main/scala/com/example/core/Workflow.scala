package com.example.core

import akka.actor._
import akka.event.{Logging, LoggingAdapter}
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import com.example.shared.PublicProtocol
import com.example.core.PrivateProtocol._

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
  var connected = Map.empty[String, (Option[String], ActorRef)]   // store only username if user is authenticated + authorized
  implicit val timeout: Timeout = Timeout(1 seconds)

  def actorRefById(connectId : String) : ActorRef = connected.get(connectId).get._2
  def usernameById(connectId : String) : Option[String] = connected.get(connectId).get._1

  def replaceValueByKey(key : String, username: Option[String], ref : ActorRef) : Unit = {
    connected -= key
    connected += (key -> (username, ref))
  }

  override def receive: Receive = {
    case IdWithInMessage(connectId, message) =>
      message match {
        case auth: PublicProtocol.login => { // TODO: multiple logins reject.
          log.info(s"auth: ${connectId}, ${auth.username}")

          (authActor ? auth).onComplete {
            case Success(Role(role)) =>
              //connected.foreach(c => log.info(c.toString()))
              connected.keys.find(connectId == _) match {
                case Some(key) =>
                  val ref = actorRefById(key)

                  role match {
                    case Some(Roles.Admin) =>
                      replaceValueByKey(key, Some(auth.username), ref)
                      ref ! PublicProtocol.login_successful(user_type = "admin")

                    case Some(Roles.User) =>
                      replaceValueByKey(key, Some(auth.username), ref)
                      ref ! PublicProtocol.login_successful(user_type = "user")

                    case None => ref ! PublicProtocol.login_failed
                  }
              }

            case Failure(err) => log.error(err.toString)
          }
        }

        case PublicProtocol.subscribe_tables | PublicProtocol.unsubscribe_tables =>
          usernameById(connectId) match {
            case None => actorRefById(connectId) ! PublicProtocol.not_authorized
            case Some(name) =>
              (authActor ? PrivateProtocol.RoleByNameRequest(name)).onComplete {
                case Success(Role(role)) =>
                  if (AuthActor.isAuthed(role)) {
                    tableManagerActor ! message
                  } else {
                    actorRefById(connectId) ! PublicProtocol.not_authorized
                  }
                case Failure(err) => log.error(err.toString)
              }
          }

        case PublicProtocol.get_tables  =>
          usernameById(connectId) match {
            case None => actorRefById(connectId) ! PublicProtocol.not_authorized
            case Some(name) =>
              (authActor ? PrivateProtocol.RoleByNameRequest(name)).onComplete {
                case Success(Role(role)) =>
                  if (AuthActor.isAdmin(role)) {
                    (tableManagerActor ? message).onComplete{
                      case Success(dto) => actorRefById(connectId) ! dto
                      case Failure(err) => log.error(err.toString)
                    }
                  } else {
                    actorRefById(connectId) ! PublicProtocol.not_authorized
                  }
                case Failure(err) => log.error(err.toString)
              }
          }

        case PublicProtocol.add_table(_,_) |
          PublicProtocol.edit_table(_,_,_) |
          PublicProtocol.delete_table(_) =>
          usernameById(connectId) match {
            case None => actorRefById(connectId) ! PublicProtocol.not_authorized
            case Some(name) =>
              (authActor ? PrivateProtocol.RoleByNameRequest(name)).onComplete {
                case Success(Role(role)) =>
                  if (AuthActor.isAdmin(role)) {
                    (tableManagerActor ? message).onComplete{
                      case Success(TableEvent(table, subscribers)) =>
                        val tablePublic : PublicProtocol.TableBase = table match {
                          case Tables.TableDTO(title, participants, updateId) => PublicProtocol.table(title, participants, updateId)
                          case Tables.TableDeleted(title) => PublicProtocol.table_deleted(title)
                        }
                        subscribers.foreach { s =>
                          connected.find(_._2._1 == s).foreach{ case (_, (_, ref)) =>
                            ref ! tablePublic
                          }
                        }
                      case Failure(err) => log.error(err.toString)
                    }
                  } else {
                    actorRefById(connectId) ! PublicProtocol.not_authorized
                  }
                case Failure(err) => log.error(err.toString)
              }
          }

        case PublicProtocol.ping(seq) =>
          actorRefById(connectId) ! PublicProtocol.pong(seq)

        case msg: PublicProtocol.failure =>
          actorRefById(connectId) ! PublicProtocol.failure

        case msg: PublicProtocol.Message =>
          log.error(s"Unmatched message: ${msg.toString}")
          actorRefById(connectId) ! PublicProtocol.failure("Error happened. Sorry :(")
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

