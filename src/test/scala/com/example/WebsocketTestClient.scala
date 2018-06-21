package com.example

import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}

import scala.concurrent.{ExecutionContext, Future}

class WebsocketTestClient(url: String, receiverRef: ActorRef)
                         (implicit system : ActorSystem, materializer: ActorMaterializer, ex: ExecutionContext) {

  val req = WebSocketRequest(uri = url)
  val flow = Http().webSocketClientFlow(req)

  val out: Source[Message, ActorRef] =
    Source.actorRef[TextMessage.Strict](bufferSize = 10, OverflowStrategy.fail)

  val in: Sink[Message, NotUsed] =
    Flow[Message]
    .collect{ case TextMessage.Strict(msg) => msg }
    .to(Sink.actorRef[String](receiverRef, "Exit"))

  val ((ws, upgradeResponse), closed) =
    out
      .viaMat(flow)(Keep.both)
      .toMat(in)(Keep.both)
      .run()

  val connected = upgradeResponse.flatMap { upgrade =>
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      Future.successful(Done)
    } else {
      throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
    }
  }

  def sendMessage(text : String): Unit = {
    ws ! TextMessage.Strict(text)
  }
}
