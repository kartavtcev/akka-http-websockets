package com.example.shared

//import cats.syntax.functor._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

object PublicProtocol {
  sealed trait Message
  case class login(username: String, password: String) extends Message
  object login_failed extends Message
  case class login_successful(user_type : String) extends Message
  case class ping(seq : Int) extends Message
  case class pong(seq: Int) extends Message

  case class fail(message: String) extends Message

  // API ADTs below are approximate & based on my memory of the test assignment

  case object not_authorized extends Message

  //sealed trait ITableMessage extends Message

  // user
  object subscribe_tables extends Message //ITableMessage
  object unsubscribe_tables extends Message // ITableMessage

  // TODO: remove
  case class single_table(table : table) extends Message // ITableMessage

  // admin
  //object get_tables extends Message // ITableMessage

  case class table_list(tables : List[table]) extends Message

  // privileged commands
  case class add_table(after_id: Int, table: table) extends Message // ITableMessage
  case class update_table(table: table) extends Message // ITableMessage
  case class remove_table(id: Int) extends Message // ITableMessage

  case class add_failed(id: Int) extends Message // This case is not present in test assignment
  case class removal_failed(id : Int) extends Message
  case class update_failed(id : Int) extends Message

  // events from the server
  case class table_added(after_id : Int, table : table) extends Message
  case class table_removed(id : Int) extends Message
  case class table_updated(table : table) extends Message

  //sealed trait TableBase
  case class table(id: Option[Int], name: String, participants: Int) //extends TableBase
  //case class table_deleted(title: String) extends TableBase
  object Table {
    implicit val encodeTable: Encoder[table] = Encoder.instance {
      case table @ PublicProtocol.table(_, _, _) => table.asJson
      //case td @ PublicProtocol.table_deleted(_) => td.asJson
    }

    implicit val decodeTable: Decoder[table] =
      //List[Decoder[table]](
        Decoder[PublicProtocol.table] //,
        //Decoder[PublicProtocol.table_deleted].widen
      //).reduceLeft(_ or _)
  }
}