package com.example.core

import akka.actor.{Actor, Props}
import akka.event.LoggingAdapter
import com.example.core.PrivateProtocol._
import com.example.shared.PublicProtocol

object TableManagerActor {
  def props(log: LoggingAdapter): Props = Props(classOf[TableManagerActor], log)
}

class TableManagerActor(val log: LoggingAdapter) extends Actor {
  // TODO: may be tables to Observable collection, persistence
  var tables = Vector[Tables.Table]()
  var subscribers = Vector[String]()

  def calcInsertId(afterId : Int) : Int = {
    if(tables.isEmpty && afterId >= -1) afterId + 1
    else if(afterId < - 1) -1
    else if(afterId == -1) {
      tables.map(_.id).min - 1
    }
    else {
      val larderIds = tables.map(_.id).filter(_ > afterId )
      Stream.from(afterId + 1) filter ( id => !larderIds.exists(_ == id)) head
    }
  }

  override def receive: Receive = {
    case IdWithInMessage(name, message) =>
      message match {
        case PublicProtocol.subscribe_tables =>
          subscribers = subscribers :+ name
          sender() ! TablesEvent(
                      Tables.listOfPrivateTablesToPublic(tables.sortBy(_.id)))

        case PublicProtocol.unsubscribe_tables =>
          subscribers = subscribers.filterNot(_ == name)

        case unmatched =>
          log.error(s"Unmatched message: ${unmatched.toString}")
      }


    case PublicProtocol.add_table(after_id, table) =>
      val newId = calcInsertId(after_id)
      if(newId < 0) {
        sender() ! TableEvent(PublicProtocol.add_failed(newId), subscribers)
      } else {
        val add = Tables.publicToPrivate(table, newId)
        tables = tables :+ add
        sender() ! TableEvent(
          PublicProtocol.table_added(after_id, Tables.privateToPublic(add)),
          subscribers)
      }


    case PublicProtocol.update_table(table) =>
      val edit = Tables.publicToPrivate(table)
      if(!tables.map(_.id).exists(_ == edit.id)) {
        sender() ! TableEvent(PublicProtocol.update_failed(edit.id), subscribers)
      } else {
        tables = tables.filterNot(_.id == edit.id)
        tables = tables :+ edit
        sender() ! TableEvent(PublicProtocol.table_updated(Tables.privateToPublic(edit)), subscribers)
      }

    case PublicProtocol.remove_table(id) =>
      if(!tables.map(_.id).exists(_ == id)) {
        sender() ! TableEvent(PublicProtocol.removal_failed(id), subscribers)
      } else {
        tables = tables.filterNot(_.id == id)
        sender() ! TableEvent(PublicProtocol.table_removed(id), subscribers)
      }
  }

}