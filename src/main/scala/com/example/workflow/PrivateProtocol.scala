package com.example.workflow

import akka.actor.ActorRef
import com.example.shared.PublicProtocol

object PrivateProtocol {
  sealed trait Event
  case class Joined(id: String, ref: ActorRef) extends Event
  case class Left(id: String) extends Event
  case class ReceivedMessage(id: String, message: String) extends Event {
    def toMessage: PublicProtocol.TextMessageWithSender = PublicProtocol.TextMessageWithSender(id, message)
  }
}