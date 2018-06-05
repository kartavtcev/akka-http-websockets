package com.example.workflow

import akka.actor._
import akka.event.{Logging, LoggingAdapter}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import com.example.shared.PublicProtocol
import com.example.workflow.PrivateProtocol._

trait Workflow {
  def flow(sender: String): Flow[PublicProtocol.Message, PublicProtocol.Message, Any]
}

object Workflow {

  def create(system: ActorSystem): Workflow = {
    lazy val log = Logging(system, classOf[Workflow])
    val workflowActor = system.actorOf(WorkflowActor.props(log), "workflowActor")

    new Workflow {
      def flow(id: String): Flow[PublicProtocol.Message, PublicProtocol.Message, Any] = {
        // sink -> UserLeft -> workflow actor; + ReceivedMessage's In
        val in =
          Flow[PublicProtocol.Message]
            .collect { case PublicProtocol.TextMessage(message) => (ReceivedMessage(id, message)) }
            .to(Sink.actorRef[Event](workflowActor, Left(id)))

        // source -> materialized actor -> NewUser + ActorRef -> workflow actor; + PublicProtocol.Message's Out
        // buffer 1 message, then fail; actor Tell ! strategy
        val out =
          Source.actorRef[PublicProtocol.Message](1, OverflowStrategy.fail)
            .mapMaterializedValue(workflowActor ! Joined(id, _))

        Flow.fromSinkAndSource(in, out)
      }
    }
  }
}

object WorkflowActor {
  def props(log: LoggingAdapter): Props = Props(classOf[WorkflowActor], log)
}

class WorkflowActor(val log: LoggingAdapter) extends Actor {
  var connected = Set.empty[(String, ActorRef)]

  override def receive: Receive = {
    case Joined(id, ref) =>
      log.info(s"new user: $id")

      connected += (id -> ref)
      broadcast(PublicProtocol.Joined(id))

    case msg: ReceivedMessage =>
      log.info(s"received message: ${msg.message}; from ${msg.id}")

      broadcast(msg.toMessage)

    case Left(id) =>
      log.info(s"user left: $id")

      connected.find(_._1 == id) match {
        case Some(entry @ (_, ref)) =>
          connected -= entry
          broadcast(PublicProtocol.Left(id))
        case _ => ()
      }
  }

  def broadcast(msg: PublicProtocol.Message): Unit = connected.foreach { case (_, ref) => ref ! msg }
}

