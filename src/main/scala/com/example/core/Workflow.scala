package com.example.workflow

import akka.actor._
import akka.event.{Logging, LoggingAdapter}
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import akka.util.Timeout
import com.example.shared.PublicProtocol
import com.example.workflow.PrivateProtocol._

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
  var connected = Map.empty[String, (Option[String], Option[Roles.Role], ActorRef)]
  implicit val timeout: Timeout = Timeout(1 seconds)

  def actorRefById(connectId : String) : ActorRef = connected.get(connectId).get._3

  override def receive: Receive = {
    case Joined(connectId, ref) =>
      log.info(s"new user: $connectId")
      //context.watch(ref) // subscribe to death watch
      connected += (connectId -> (None, None, ref))
      //broadcast(PublicProtocol.Joined(id))

    case IdWithInMessage(connectId, message) =>
      message match {
        case auth: PublicProtocol.login => {              // TODO: multiple logins reject.
          log.info(s"auth: ${connectId}")

          (authActor ? auth).onComplete {
            case Success(Role(role)) =>
              //connected.foreach(c => log.info(c.toString()))
              connected.keys.find(connectId == _) match {
                case Some(key) =>
                  val ref = actorRefById(key)
                  connected -= key
                  connected += (key -> (Some(auth.username), role, ref))

                  role match {
                    case Some(Roles.Admin) => ref ! PublicProtocol.login_successful(user_type = "admin")
                    case Some(Roles.User) => ref ! PublicProtocol.login_successful(user_type = "user")
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
      }

    case Left(connectId) =>
      log.info(s"user left: $connectId")
      connected = connected.filterNot(_._1 == connectId)

      /*
    case Terminated(ref) =>
      log.info(s"user left: ${ref.toString()}")
      connected = connected.filterNot(_._1 == ref)
      */
  }

  //def broadcast(msg: PublicProtocol.Message): Unit = connected.foreach { case (_, ref) => ref ! msg }
}

