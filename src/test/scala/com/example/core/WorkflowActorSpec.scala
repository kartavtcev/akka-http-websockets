package com.example.core

import akka.actor.ActorSystem
import akka.event.Logging
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.example.shared.PublicProtocol
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class WorkflowActorSpec(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with BeforeAndAfterAll with MustMatchers with ImplicitSender{

  def this() = this(ActorSystem("com-example-test"))

  override def afterAll {
    system.terminate()
  }

  "Workflow actor" must {
    "Process Join. Reply pong to ping message." in {

      lazy val log = Logging(system, classOf[WorkflowActorSpec])
      val authActor = TestProbe()
      val tableManagerActor = TestProbe()

      //val authActor = system.actorOf(AuthActor.props(log), "authActor")
      //val tableManagerActor = system.actorOf(TableManagerActor.props(log), "tableManagerActor")

      val workflowActor = system.actorOf(WorkflowActor.props(log, authActor.ref, tableManagerActor.ref), "workflowActor")

      workflowActor ! PrivateProtocol.Joined("connect id UUID", testActor)
      workflowActor ! PrivateProtocol.IdWithInMessage("connect id UUID", PublicProtocol.ping(1))

      expectMsg(1 second, PublicProtocol.pong(1))
    }
  }
}