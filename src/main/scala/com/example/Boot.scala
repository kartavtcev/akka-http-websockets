package com.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

object Server {

  var _system: Option[ActorSystem] = None
  var _binding: Option[Future[ServerBinding]] = None

  def start: String = {
    implicit val system: ActorSystem = ActorSystem("com-example-httpServer")
    _system = Some(system)
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val config = system.settings.config
    val interface = config.getString("app.interface")
    val port = config.getInt("app.port")
    val wsUrl = config.getString("app.ws-url")

    val service = new Webservice (wsUrl)
    lazy val routes: Route = Route.seal { service.route }

    val binding = Http().bindAndHandle(routes, interface, port)
    _binding = Some(binding)
    binding.onComplete {
      case Success(binding) ⇒
        val localAddress = binding.localAddress
        println(s"Server is listening on ${localAddress.getHostName}:${localAddress.getPort}")
      case Failure(e) ⇒
        println(s"Binding failed with ${e.getMessage}")
        system.terminate()
    }

    s"ws://${interface}:${port}/${wsUrl}"
  }

  def stop: Unit = {
    (_system, _binding) match {
      case (Some(system), Some(binding)) =>
        binding
          .flatMap(_.unbind())
          .onComplete(_ => {
            system.terminate()
            println("Server stopped")
            _system = None
            _binding = None
          })
      case _ => ()
    }
  }
}

object Boot extends App {
  Server.start
}