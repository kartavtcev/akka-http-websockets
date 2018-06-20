package com.example

import akka.actor._
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import akka.testkit._
import org.scalatest._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._


class IntergrationSpec(_system: ActorSystem)  extends TestKit(_system) with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("com-example-test"))

  var params: Option[(ActorSystem, Future[ServerBinding], String)] = None

  override def beforeAll: Unit = {
    params = Some(Server.start)
  }

  override def afterAll: Unit = {
    params match {
      case Some((system, future, _)) => Server.stop(system, future)
      case _ => ()
    }
  }

  "Service" should {
    "start; process user login, subscribe; admin add table; table added event; not authorized; stop" in {

      implicit val system = _system
      implicit val materializer = ActorMaterializer()

      val url = params.get._3

      val user1TestProve = TestProbe()
      val adminTestProbe = TestProbe()

      val user2TestProve = TestProbe()

      val adminClient = new WebsocketTestClient(url, adminTestProbe.ref)
      val user1Client = new WebsocketTestClient(url, user1TestProve.ref)

      val user2Client = new WebsocketTestClient(url, user2TestProve.ref)


      adminClient.sendMessage("""{"$type":"login", "username":"admin", "password": "admin"}""")
      adminTestProbe.expectMsg(2 second, """{"user_type":"admin","$type":"login_successful"}""")

      user1Client.sendMessage("""{"$type":"login", "username":"user1234", "password": "password1234"}""")
      user1TestProve.expectMsg(1 second, """{"user_type":"user","$type":"login_successful"}""")

      user1Client.sendMessage("""{"$type":"subscribe_tables"}""")
      user1TestProve.expectMsg(1 second, """{"tables":[{"id":2,"name":"Blackjack","participants":15},{"id":3,"name":"Roulette","participants":10}],"$type":"table_list"}""")

      adminClient.sendMessage("""{
                            "$type": "add_table",
                            "after_id": 1,
                            "table": {
                              "name": "table - Foo Fighters",
                              "participants": 4
                            }
                          }""")

      user1TestProve.expectMsg("""{"after_id":1,"table":{"id":4,"name":"table - Foo Fighters","participants":4},"$type":"table_added"}""")

      user1Client.sendMessage("""{
                            "$type": "add_table",
                            "after_id": 1,
                            "table": {
                              "name": "table - Foo Fighters",
                              "participants": 4
                            }
                          }""")
      user1TestProve.expectMsg("""{"$type":"not_authorized"}""")

      user2Client.sendMessage("""{"$type":"subscribe_tables"}""")
      user2TestProve.expectMsg("""{"$type":"not_authorized"}""")

      user2Client.sendMessage("incorrect json input")
      user2TestProve.expectMsg("""{"message":"Error happened. Sorry :(","$type":"fail"}""")
    }
  }
}


// TODO ???
/*
Server stopped
[ERROR] [06/20/2018 08:15:59.101] [com-example-httpServer-akka.actor.default-dispatcher-8] [Webservice(akka://com-example-httpServer)] WS stream failed with akka.stream.AbruptTerminationException: Processor actor [Actor[akka://com-example-httpServer/system/StreamSupervisor-0/flow-1-0-detacher#-286950123]] terminated abruptly
[ERROR] [06/20/2018 08:15:59.117] [com-example-httpServer-akka.actor.default-dispatcher-2] [Webservice(akka://com-example-httpServer)] WS stream failed with akka.stream.AbruptTerminationException: Processor actor [Actor[akka://com-example-httpServer/system/StreamSupervisor-0/flow-2-0-detacher#-1847442788]] terminated abruptly
[ERROR] [06/20/2018 08:15:59.117] [com-example-httpServer-akka.actor.default-dispatcher-3] [akka.actor.ActorSystemImpl(com-example-httpServer)] Websocket handler failed with Processor actor [Actor[akka://com-example-httpServer/system/StreamSupervisor-0/flow-1-0-detacher#-286950123]] terminated abruptly (akka.stream.AbruptTerminationException: Processor actor [Actor[akka://com-example-httpServer/system/StreamSupervisor-0/flow-1-0-detacher#-286950123]] terminated abruptly)
[ERROR] [06/20/2018 08:15:59.117] [com-example-httpServer-akka.actor.default-dispatcher-9] [akka.actor.ActorSystemImpl(com-example-httpServer)] Websocket handler failed with Processor actor [Actor[akka://com-example-httpServer/system/StreamSupervisor-0/flow-2-0-detacher#-1847442788]] terminated abruptly (akka.stream.AbruptTerminationException: Processor actor [Actor[akka://com-example-httpServer/system/StreamSupervisor-0/flow-2-0-detacher#-1847442788]] terminated abruptly)
*/