package com.example.shared

object PublicProtocol {
  sealed trait Message
  case class login(username: String, password: String) extends Message
  object login_failed extends Message
  case class login_successful(user_type : String) extends Message
  case class ping(seq : Int) extends Message
  case class pong(seq: Int) extends Message

  case class failure(message: String) extends Message

  // API ADTs below are approximate & based on my memory of the test assignment

  case object not_authorized extends Message

  // user
  object subscribe_tables extends Message
  object unsubscribe_tables extends Message

  // admin
  case class table(participants : Int, title: String, update_id: Long)
  object get_tables extends Message
  case class tables(tables : List[table]) extends Message


  case class add_table(title: String, participants: Int) extends Message  // no response
  case class edit_table(title: String, participants: Int, update_id: Long) extends Message // no response is success
  case class delete_table(title: String) extends Message // req

  case class table_deleted(title: String) extends Message // resp
}