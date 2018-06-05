package com.example.cli

import scala.collection.immutable.Seq
import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.ws._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._

import scala.concurrent.{ Future, Promise }

// https://doc.akka.io/docs/akka-http/current/client-side/websocket-support.html

object Main extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val config = system.settings.config
  val interface = config.getString("app.interface")
  val port = config.getInt("app.port")
  implicit val wsUrl = config.getString("app.ws-url")

  // print each incoming strict text message
  val printSink: Sink[Message, Future[Done]] =
    Sink.foreach {
      case message: TextMessage.Strict =>
        println(message.text)
      case _ => ()
    }

  val textToSend = """{"TextMessage":{"message":"TTTesTTT"}}"""

  val helloSource = //: Source[Message, NotUsed] =
    Source(List(TextMessage(textToSend), TextMessage(textToSend)))
      .concatMat(Source.maybe[Message])(Keep.right)
  //Source.single(TextMessage(textToSend))

  // the Future[Done] is the materialized value of Sink.foreach
  // and it is completed when the stream completes
  val flow: Flow[Message, Message, Promise[Option[Message]]] = //Flow[Message, Message, Future[Done]] =
    Flow.fromSinkAndSourceMat(printSink, helloSource)(Keep.right)

  // upgradeResponse is a Future[WebSocketUpgradeResponse] that
  // completes or fails when the connection succeeds or fails
  // and closed is a Future[Done] representing the stream completion from above
  val (upgradeResponse, promise) =
    Http().singleWebSocketRequest(
      WebSocketRequest(
        s"ws://${interface}:${port}/${wsUrl}?id=admin",
        extraHeaders = Seq(Authorization(
          BasicHttpCredentials("admin", "admin")
        ))
      ),
      flow
    )

  val connected = upgradeResponse.map { upgrade =>
    // just like a regular http request we can access response status which is available via upgrade.response.status
    // status code 101 (Switching Protocols) indicates that server support WebSockets
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      Done
    } else {
      throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
    }
  }

  // in a real application you would not side effect here
  // and handle errors more carefully
  connected.onComplete(println)

  // at some later time we want to disconnect
  promise.success(None)

  //closed.foreach(_ => println("closed"))
}
