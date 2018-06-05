package com.example

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.Flow
import com.example.shared.PublicProtocol
import com.example.workflow.Workflow
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import scala.concurrent.ExecutionContext
import scala.util.Failure

class Webservice(wsUrl: String) (implicit system: ActorSystem, ec: ExecutionContext) extends Directives {

  val workflow = Workflow.create(system)
  lazy val log = Logging(system, classOf[Webservice])

  /*
  def userPassAuthenticator(credentials: Credentials): Option[Roles.Role] = {
    credentials match {
      case p @ Credentials.Provided(id) if p.verify("admin") => Some(Roles.Admin) // Do not repeat this at home! Store PWD as hash + salt.
      case _ => None
    }
  }

  def userPassAuthenticatorPassAll(credentials: Credentials): Option[Roles.Role] = {
    credentials match {
      case p @ Credentials.Provided(id) if p.verify("admin") => Some(Roles.Admin) // Do not repeat this at home! Store PWD as hash + salt.
      case _ => Some(Roles.User) //None
    }
  }
*/

  def route: Route = {
    pathPrefix(wsUrl) {
      concat(

        /*pathPrefix("admin") {
          // separate admin auth
          authenticateBasic(realm = "secure site", userPassAuthenticator) { auth => complete(s"${auth.toString}") }
        },*/
        //parameter("id") { id =>
          handleWebSocketMessages(websocketChatFlow)
          /*
          // admin auth for websockets
          // TODO: fix IntelliJ IDEA incorrect red highlighting
          authenticateBasic(realm = "secure site", userPassAuthenticatorPassAll) { auth =>
            log.info(s"id = $id, auth = $auth")
            handleWebSocketMessages(websocketChatFlow(id = id))
          }*/
        //}
      )
    }
  }

  def websocketChatFlow: Flow[Message, Message, Any] =
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
          PublicProtocol.TextMessage("failure" ,"Error happened. Sorry :(")
      }
      .via(workflow.flow)
      .map {
        case msg: PublicProtocol.Message ⇒ TextMessage.Strict(msg.asJson.noSpaces)
      }
      .via(reportErrorsFlow)

  def reportErrorsFlow[T]: Flow[T, T, Any] =
    Flow[T]
      .watchTermination()((_, f) => f.onComplete {
        case Failure(cause) =>
          log.error(s"WS stream failed with $cause")
        case _ =>
      })
}