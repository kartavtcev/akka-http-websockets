package com.example.shared

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

  sealed trait ITableMessage extends Message

  // user
  object subscribe_tables extends ITableMessage
  object unsubscribe_tables extends ITableMessage

  case class single_table(table : TableBase) extends ITableMessage

  // admin
  object get_tables extends ITableMessage

  case class tables(tables : List[TableBase]) extends ITableMessage

  case class add_table(title: String, participants: Int) extends ITableMessage
  case class edit_table(title: String, participants: Int, update_id: Long) extends ITableMessage
  case class delete_table(title: String) extends ITableMessage


  sealed trait TableBase
  case class table(title: String, participants : Int, update_id: Long) extends TableBase
  case class table_deleted(title: String) extends TableBase
}