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
  var tables = Map[Int, Tables.Table](
    2 -> Tables.Table(2, "Blackjack", 15),
    3 -> Tables.Table(3, "Roulette", 10))
  //,
    //"Dragon Tiger" -> Tables.TableDeleted("Dragon Tiger"))

  var subscribers = Vector[String]()


  def calcInsertId(afterId : Int) : Int = {
    if(afterId == -1) {
      tables.keys.min - 1
    }
    else {
      val larderIds = tables.keys.filter(_ > afterId )
      //val first = ids.headOption.getOrElse(-1)
      Stream.from(afterId + 1) takeWhile( id => !larderIds.exists(_ == id)) head
    }
  }

  override def receive: Receive = {
    case IdWithInMessage(name, message) =>
      message match {
        case PublicProtocol.subscribe_tables =>
          subscribers = subscribers :+ name
          sender() ! TablesEvent(
                      Tables.listOfPrivateTablesToPublic(tables.values.to[collection.immutable.Seq]))

        case PublicProtocol.unsubscribe_tables =>
          subscribers = subscribers.filterNot(_ == name)

        case unmatched =>
          log.error(s"Unmatched message: ${unmatched.toString}")
      }


    case PublicProtocol.add_table(after_id, table) =>   // TODO: after_id
      val newId = calcInsertId(after_id)
      //tables -= table.name
      if(newId < 0) {
        sender() ! TableEvent(PublicProtocol.add_failed(newId), subscribers)
      } else {
        val add = Tables.publicToPrivate(table, newId)
        tables += newId -> add
        sender() ! TableEvent(
          PublicProtocol.table_added(after_id, Tables.privateToPublic(add)),
          subscribers)
      }


    case PublicProtocol.update_table(table) =>
      val edit = Tables.publicToPrivate(table)
      tables -= table.name
      tables += table.name -> edit
      sender() ! TableEvent(edit, subscribers)

    case PublicProtocol.remove_table(id) =>
      //val delete = Tables.TableDeleted(title)
      tables -= id
      //tables += id.toString() -> delete
      sender() ! TableEvent(delete, subscribers)

  }

}