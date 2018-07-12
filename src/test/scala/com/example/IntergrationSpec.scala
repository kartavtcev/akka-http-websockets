package com.example

import akka.actor._
import akka.stream.ActorMaterializer
import akka.testkit._
import org.scalatest._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


class IntergrationSpec(_system: ActorSystem)  extends TestKit(_system) with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("com-example-test"))

  var url: Option[String] = None

  override def beforeAll: Unit = {
    url = Some(Server.start)
  }

  override def afterAll: Unit = {
    Server.stop
  }

  "Service" should {
    "start; process user login, subscribe; admin add table; table added event; not authorized; stop" in {

      implicit val system = _system
      implicit val materializer = ActorMaterializer()

      val user1TestProve = TestProbe()
      val adminTestProbe = TestProbe()

      val user2TestProve = TestProbe()

      val adminClient = new WebsocketTestClient(url.get, adminTestProbe.ref)
      val user1Client = new WebsocketTestClient(url.get, user1TestProve.ref)

      val user2Client = new WebsocketTestClient(url.get, user2TestProve.ref)


      adminClient.sendMessage("""{"$type":"login", "username":"admin", "password": "admin"}""")
      adminTestProbe.expectMsg(2 second, """{"user_type":"admin","$type":"login_successful"}""")

      adminClient.sendMessage("""{
                            "$type": "add_table",
                            "after_id": 2,
                            "table": {
                              "name": "Roulette",
                              "participants": 10
                            }
                          }""")
      adminTestProbe.expectNoMessage(500 millis)
      adminClient.sendMessage("""{
                            "$type": "add_table",
                            "after_id": 1,
                            "table": {
                              "name": "Blackjack",
                              "participants": 15
                            }
                          }""")
      adminTestProbe.expectNoMessage(500 millis)

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