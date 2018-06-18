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
  var tables = Map[Int, Tables.TableBase](
    1 -> Tables.TableDTO(1, "Blackjack", 15),
    3 -> Tables.TableDTO(3, "Roulette", 10))
  //,
    //"Dragon Tiger" -> Tables.TableDeleted("Dragon Tiger"))

  var subscribers = Vector[String]()

  /*
  def insertId(afterId : Int) : Int = {
    val ids = tables.keys.toList.sorted /// TODO
    //val first = ids.headOption.getOrElse(-1)
  }
  */

  override def receive: Receive = {
    case IdWithInMessage(name, message) =>
      message match {
        case PublicProtocol.subscribe_tables =>
          subscribers = subscribers :+ name
          sender() ! TablesEvent(tables.values.to[collection.immutable.Seq])

        case PublicProtocol.unsubscribe_tables =>
          subscribers = subscribers.filterNot(_ == name)

        case unmatched =>
          log.error(s"Unmatched message: ${unmatched.toString}")
      }

/*
    case PublicProtocol.add_table(after_id, table) =>   // TODO: after_id
      val add = Tables.TableDTO(after_id, table.name, table.participants)
      tables -= table.name
      tables += table.name -> add
      sender() ! TableEvent(add, subscribers)

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
        */
  }

}