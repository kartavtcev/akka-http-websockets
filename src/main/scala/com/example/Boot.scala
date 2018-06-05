package com.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success }

object Boot extends App {

  implicit val system: ActorSystem = ActorSystem("com-example-httpServer")
  import system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val config = system.settings.config
  val interface = config.getString("app.interface")
  val port = config.getInt("app.port")
  val wsUrl = config.getString("app.ws-url")

  val service = new Webservice (wsUrl)
  lazy val routes: Route = Route.seal { service.route }

  val binding = Http().bindAndHandle(routes, interface, port)
  binding.onComplete {
    case Success(binding) ⇒
      val localAddress = binding.localAddress
      println(s"Server is listening on ${localAddress.getHostName}:${localAddress.getPort}")
    case Failure(e) ⇒
      println(s"Binding failed with ${e.getMessage}")
      system.terminate()
  }

  Await.result(system.whenTerminated, Duration.Inf)
}