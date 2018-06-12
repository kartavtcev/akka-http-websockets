package com.example.core

import akka.actor.{Actor, Props}
import akka.event.LoggingAdapter
import com.example.core.PrivateProtocol._
import com.example.shared.PublicProtocol


object TableManagerActor {
  def props(log: LoggingAdapter): Props = Props(classOf[TableManagerActor], log)
}

class TableManagerActor(val log: LoggingAdapter) extends Actor {
  // TODO: tables to Observable collection
  var tables = Map[String, Tables.TableBase] (
    "Blackjack" -> Tables.TableDTO("Blackjack", 15, 1),
    "Roulette" -> Tables.TableDTO("Roulette", 10, 3),
    "Dragon Tiger" -> Tables.TableDeleted("Dragon Tiger"))

  var subscribers = Vector[String]()

  // add / edit / delete methods

  override def receive: Receive = {
    case  IdWithInMessage(name, message) =>
      message match {
        case PublicProtocol.subscribe_tables =>
          subscribers = subscribers :+ name
        case PublicProtocol.unsubscribe_tables =>
          subscribers = subscribers.filterNot(_ == name)

        case PublicProtocol.get_tables =>
          sender() ! PublicProtocol.tables(
            tables.values.collect {
              case Tables.TableDTO(title, participants, updateId) => PublicProtocol.table(title, participants, updateId).asInstanceOf[PublicProtocol.TableBase]
              case Tables.TableDeleted(title) => PublicProtocol.delete_table(title).asInstanceOf[PublicProtocol.TableBase]
            }.toList)

        case PublicProtocol.add_table(title, participants) =>
          val add = Tables.TableDTO(title, participants, 1)
          tables -= title
          tables += title -> add
          sender() ! TableEvent(add, subscribers)

        case PublicProtocol.edit_table(title, participants, update_id) =>
          val edit = Tables.TableDTO(title, participants, update_id)
          tables -= title
          tables += title -> edit
          sender() ! TableEvent(edit, subscribers)

        case PublicProtocol.delete_table(title) =>
          val delete = Tables.TableDeleted(title)
          tables -= title
          tables += title -> delete
          sender() ! TableEvent(delete, subscribers)

      }
  }
}