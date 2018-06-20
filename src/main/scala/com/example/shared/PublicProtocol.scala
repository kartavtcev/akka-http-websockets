package com.example.shared

object PublicProtocol {
  sealed trait Message
  case class login(username: String, password: String) extends Message
  object login_failed extends Message
  case class login_successful(user_type : String) extends Message
  case class ping(seq : Int) extends Message
  case class pong(seq: Int) extends Message

  case class fail(message: String) extends Message // This case is not present in test assignment

  case object not_authorized extends Message

  // user
  object subscribe_tables extends Message
  object unsubscribe_tables extends Message

  // admin
  case class table_list(tables : List[table]) extends Message

  // privileged commands
  case class add_table(after_id: Int, table: table) extends Message
  case class update_table(table: table) extends Message
  case class remove_table(id: Int) extends Message

  case class add_failed(id: Int) extends Message // This case is not present in test assignment
  case class removal_failed(id : Int) extends Message
  case class update_failed(id : Int) extends Message

  // events from the server
  case class table_added(after_id : Int, table : table) extends Message
  case class table_removed(id : Int) extends Message
  case class table_updated(table : table) extends Message

  case class table(id: Option[Int], name: String, participants: Int)
}