package com.example

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.Flow
import com.example.core.{JsonModule, Workflow}
import com.example.shared.PublicProtocol

import scala.concurrent.ExecutionContext
import scala.util.Failure

class Webservice(wsUrl: String) (implicit system: ActorSystem, ec: ExecutionContext) extends Directives {

  val workflow = Workflow.create(system)
  lazy val log = Logging(system, classOf[Webservice])

  def route: Route = {
    pathPrefix(wsUrl) {
      handleWebSocketMessages(websocketChatFlow)
    }
  }

  def websocketChatFlow: Flow[Message, Message, Any] = {
    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) ⇒ msg
        //case TextMessage.Streamed(msg) => msg // Ignore if message is not in one chunk, unlikely, as messages are small
      }
      .map(JsonModule.decode(_))
      .map {
        case Right(msg) => msg
        case Left(err) =>
          log.error(err.toString())
          PublicProtocol.failure("Error happened. Sorry :(")
      }
      .via(workflow.flow)
      .map {
        case msg: PublicProtocol.Message ⇒ TextMessage.Strict(JsonModule.toJson(msg))
      }
      .via(reportErrorsFlow)
  }
  /*
   Above is WebSockets interaction using Akka Stream lib.
   Another way to implement it could be with Monix reactive observables (familiar to me from .NET Rx library)
   https://github.com/monix/monix-sample/blob/master/client/src/main/scala/client/SimpleWebSocketClient.scala
  */

  def reportErrorsFlow[T]: Flow[T, T, Any] =
    Flow[T]
      .watchTermination()((_, f) => f.onComplete {
        case Failure(cause) =>
          log.error(s"WS stream failed with $cause")
        case _ =>
      })
}