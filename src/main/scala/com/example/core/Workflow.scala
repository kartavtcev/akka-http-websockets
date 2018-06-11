package com.example.core

import akka.actor._
import akka.event.{Logging, LoggingAdapter}
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import akka.util.Timeout
import com.example.shared.PublicProtocol
import com.example.core.PrivateProtocol._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}


trait Workflow {
  def flow: Flow[PublicProtocol.Message, PublicProtocol.Message, Any]
}

object Workflow {
  def uuid = java.util.UUID.randomUUID.toString

  def create(system: ActorSystem)(implicit ec: ExecutionContext): Workflow = {
    lazy val log = Logging(system, classOf[Workflow])
    val authActor = system.actorOf(AuthActor.props(log), "authActor")

    // TODO: flow is served by single actor, i.e. similar to single thread. => Workflow actor must not stuck/lock/wait.
    val workflowActor = system.actorOf(WorkflowActor.props(log, authActor), "workflowActor")

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
  def props(log: LoggingAdapter, authActor: ActorRef)(implicit ec: ExecutionContext): Props = Props(new WorkflowActor(log, authActor))
}

class WorkflowActor(val log: LoggingAdapter, val authActor : ActorRef)(implicit ec: ExecutionContext) extends Actor {
  var connected = Map.empty[String, (Option[String], ActorRef)]   // store only username if user is authenticated + authorized
  implicit val timeout: Timeout = Timeout(1 seconds)

  def actorRefById(connectId : String) : ActorRef = connected.get(connectId).get._2
  def replaceValueByKey(key : String, username: Option[String], ref : ActorRef) : Unit = {
    connected -= key
    connected += (key -> (username, ref))
  }

  override def receive: Receive = {
    case IdWithInMessage(connectId, message) =>
      message match {
        case auth: PublicProtocol.login => {              // TODO: multiple logins reject.
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
        case PublicProtocol.ping(seq) =>
          actorRefById(connectId) ! PublicProtocol.pong(seq)
        case msg : PublicProtocol.failure =>
          actorRefById(connectId) ! PublicProtocol.failure

          // TODO: case

        case msg : PublicProtocol.Message =>
          log.warning(s"Unmatched message: ${msg.toString}")
          PublicProtocol.failure("Error happened. Sorry :(")
      }

    case Joined(connectId, ref) =>
      log.info(s"new user: $connectId")
      connected += (connectId -> (None, ref))

    case Left(connectId) =>
      log.info(s"user left: $connectId")
      connected = connected.filterNot(_._1 == connectId)
  }

  //def broadcast(msg: PublicProtocol.Message): Unit = connected.foreach { case (_, ref) => ref ! msg }
}

