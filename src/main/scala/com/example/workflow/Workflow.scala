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

  def create(system: ActorSystem)(implicit ec: ExecutionContext): Workflow = {
    lazy val log = Logging(system, classOf[Workflow])
    val authActor = system.actorOf(AuthActor.props(log), "authActor")
    val workflowActor = system.actorOf(WorkflowActor.props(log, authActor), "workflowActor")

    new Workflow {
      def flow: Flow[PublicProtocol.Message, PublicProtocol.Message, Any] = {
        // sink -> UserLeft -> workflow actor; + ReceivedMessage's In
        val in =
          Flow[PublicProtocol.Message]
            //.collect { case PublicProtocol.TextMessage(message) => (ReceivedMessage(, message)) }
            .to(Sink.actorRef[PublicProtocol.Message](workflowActor, Left))

        // source -> materialized actor -> NewUser + ActorRef -> workflow actor; + PublicProtocol.Message's Out
        // buffer 1 message, then fail; actor Tell ! strategy
        val out =
          Source.actorRef[PublicProtocol.Message](1, OverflowStrategy.fail)
            .mapMaterializedValue(workflowActor ! Joined(_, Roles.Unknown))

        Flow.fromSinkAndSource(in, out)
      }
    }
  }
}

object WorkflowActor {
  def props(log: LoggingAdapter, authActor: ActorRef)(implicit ec: ExecutionContext): Props = Props(new WorkflowActor(log, authActor))
}

class WorkflowActor(val log: LoggingAdapter, val authActor : ActorRef)(implicit ec: ExecutionContext) extends Actor {
  var connected = Map.empty[ActorRef, (Option[String], Roles.Role)]
  implicit val timeout: Timeout = Timeout(5 seconds)

  override def receive: Receive = {
    case Joined(ref, role) =>
      log.info(s"new user: ${ref.toString()}")
      //context.watch(ref) // subscribe to death watch
      connected += (ref -> (None, role))
      //broadcast(PublicProtocol.Joined(id))

    case msg : PublicProtocol.Auth =>
      val ref = sender()
      (authActor ? msg).onComplete {        // TODO: future.flatMap
        case Success(Role(role)) =>
          connected.keys.find(_ == ref) match {
            case Some(key) =>
              val mapV = connected.get(key).get
              connected -= key
              connected += (key -> (Some(msg.username), role))
          }
        role match {
          case Roles.Admin => sender() ! PublicProtocol.LoginSuccess(user_type = "admin")
          case Roles.User =>  sender() ! PublicProtocol.LoginSuccess(user_type = "user")
          case Roles.Unknown => sender() ! PublicProtocol.LoginFailed
        }

        case Failure(err) => log.error(err.toString)
      }

    case PublicProtocol.Heartbeat(_, seq) => sender() ! PublicProtocol.Heartbeat("pong", seq)

      /*
    case msg: PublicProtocol.Message =>
      log.info(s"received message: ${msg.message}; from ${msg.id}")

      //broadcast(msg.toMessage)
*/
    case Left =>
      val ref = sender()
      log.info(s"user left: ${ref.toString()}")
      connected = connected.filterNot(_._1 == ref)

      /*
    case Terminated(ref) =>
      log.info(s"user left: ${ref.toString()}")
      connected = connected.filterNot(_._1 == ref)
      */
      /*
    case Left(id) =>
      log.info(s"user left: $id")

      connected.keys.find(_ == id) match {
        case Some(key) =>
          connected = connected.filterKeys(_ != key)
          //broadcast(PublicProtocol.Left(id))
        case _ => ()
      }
      */
  }

  //def broadcast(msg: PublicProtocol.Message): Unit = connected.foreach { case (_, ref) => ref ! msg }
}

