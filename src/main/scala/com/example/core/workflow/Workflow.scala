package com.example.core.workflow

import akka.actor._
import akka.event.Logging
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import com.example.core.PrivateProtocol._
import com.example.core._
import com.example.shared.PublicProtocol

import scala.concurrent.ExecutionContext

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
          Source.actorRef[PublicProtocol.Message](10, OverflowStrategy.fail)
            .mapMaterializedValue(workflowActor ! Joined(connectId, _))

        Flow.fromSinkAndSource(in, out)
      }
    }
  }
}