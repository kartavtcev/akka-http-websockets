package com.example

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.Flow

import scala.concurrent.ExecutionContext
import scala.util.Failure

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.parser.decode
import io.circe.syntax._

import com.example.shared.PublicProtocol
import com.example.workflow.Workflow

class Webservice(wsUrl: String) (implicit system: ActorSystem, ec: ExecutionContext) extends Directives {

  val workflow = Workflow.create(system)
  lazy val log = Logging(system, classOf[Webservice])

  def route: Route = {
    pathPrefix(wsUrl) {
          handleWebSocketMessages(websocketChatFlow)
    }
  }

  def websocketChatFlow: Flow[Message, Message, Any] = {
    implicit val genDevConfig: Configuration = Configuration.default.withDiscriminator("$type")

    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) ⇒ msg
        //case TextMessage.Streamed(msg) => msg // Ignore if message is not in one chunk, unlikely, as messages are small
      }
      .map(decode[PublicProtocol.Message](_))
      .map {
        case Right(msg) => msg
        case Left(err) =>
          log.error(err.toString())
          PublicProtocol.failure("Error happened. Sorry :(")
      }
      .via(workflow.flow)
      .map {
        case msg: PublicProtocol.Message ⇒ TextMessage.Strict(msg.asJson.noSpaces)
      }
      .via(reportErrorsFlow)
  }

  def reportErrorsFlow[T]: Flow[T, T, Any] =
    Flow[T]
      .watchTermination()((_, f) => f.onComplete {
        case Failure(cause) =>
          log.error(s"WS stream failed with $cause")
        case _ =>
      })
}