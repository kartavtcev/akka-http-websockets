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

            //Sink.actorRef[PublicProtocol.Message]
              //.mapMaterializedValue(workflowActor ! Left)

        // source -> materialized actor -> NewUser + ActorRef -> workflow actor; + PublicProtocol.Message's Out
        // buffer 1 message, then fail; actor Tell ! strategy
        val out =
          Source.actorRef[PublicProtocol.Message](1, OverflowStrategy.fail)
            .mapMaterializedValue(workflowActor ! Joined(connectId, Roles.Unknown, _))

        Flow.fromSinkAndSource(in, out)
      }
    }
  }
}

object WorkflowActor {
  def props(log: LoggingAdapter, authActor: ActorRef)(implicit ec: ExecutionContext): Props = Props(new WorkflowActor(log, authActor))
}

class WorkflowActor(val log: LoggingAdapter, val authActor : ActorRef)(implicit ec: ExecutionContext) extends Actor {
  var connected = Map.empty[String, (Option[String], Roles.Role, ActorRef)]
  implicit val timeout: Timeout = Timeout(1 seconds)

  override def receive: Receive = {
    case Joined(connectId, role, ref) =>
      log.info(s"new user: $connectId")
      //context.watch(ref) // subscribe to death watch
      connected += (connectId -> (None, role, ref))
      //broadcast(PublicProtocol.Joined(id))

    case IdWithInMessage(connectId, message) => //idWithInMessage : IdWithInMessage =>
      message match {
        case auth: PublicProtocol.login => {
          //(connectId : String, auth: PublicProtocol.Auth) => //idWithInMessage : IdWithInMessage => // TODO: incoming message from Sink has ref Actor[akka://com-example-httpServer/deadLetters]
          log.info(s"auth: ${connectId}")

          (authActor ? auth).onComplete {
            // TODO: future.flatMap
            case Success(Role(role)) =>
              connected.foreach(c => log.info(c.toString()))
              connected.keys.find(connectId == _) match {
                case Some(key) =>
                  val ref = connected.get(key).get._3
                  connected -= key
                  connected += (key -> (Some(auth.username), role, ref))

                  role match {
                    case Roles.Admin => ref ! PublicProtocol.login_successful(user_type = "admin")
                    case Roles.User => ref ! PublicProtocol.login_successful(user_type = "user")
                    case Roles.Unknown => ref ! PublicProtocol.login_failed
                  }
              }

            case Failure(err) => log.error(err.toString)
          }
        }
      }

    case (connectId : String, PublicProtocol.ping(seq)) =>
      connected.get(connectId).get._3 ! PublicProtocol.pong(seq)

      /*
    case msg: PublicProtocol.Message =>
      log.info(s"received message: ${msg.message}; from ${msg.id}")

      //broadcast(msg.toMessage)
*/
    case Left(connectId) =>
      log.info(s"user left: $connectId")
      connected = connected.filterNot(_._1 == connectId)

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

