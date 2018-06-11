package com.example.core

import akka.actor.{Actor, Props}
import akka.event.LoggingAdapter

object TableManagerActor {
  def props(log: LoggingAdapter): Props = Props(classOf[TableManagerActor], log)
}

class TableManagerActor(val log: LoggingAdapter) extends Actor {
  // collect table id -> n*Option[Table]
  // collect subscribers
  // add / edit / delete methods

  override def receive: Receive = {
    case _  => Unit
  }
}