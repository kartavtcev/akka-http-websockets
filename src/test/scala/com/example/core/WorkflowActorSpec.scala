package com.example.core

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.event.Logging
import akka.testkit.{ImplicitSender, TestKit, TestProbe}

import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import com.example.shared.PublicProtocol

class WorkflowActorSpec(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with BeforeAndAfterAll with MustMatchers with ImplicitSender{

  def this() = this(ActorSystem("com-example-test"))

  override def afterAll {
    system.terminate()
  }

  "Workflow actor" must {
    "process Join, reply pong to ping message." in {

      lazy val log = Logging(system, classOf[WorkflowActorSpec])
      val authActor = TestProbe()
      val tableManagerActor = TestProbe()

      //val authActor = system.actorOf(AuthActor.props(log), "authActor")
      //val tableManagerActor = system.actorOf(TableManagerActor.props(log), "tableManagerActor")

      val workflowActor = system.actorOf(WorkflowActor.props(log, authActor.ref, tableManagerActor.ref), "workflowActor")

      val uuid = "connect UUID"
      val seq = 1

      workflowActor ! PrivateProtocol.Joined(uuid, testActor)
      workflowActor ! PrivateProtocol.IdWithInMessage(uuid, PublicProtocol.ping(seq))

      expectMsg(1 second, PublicProtocol.pong(seq))
    }
  }
}