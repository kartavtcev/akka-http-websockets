package com.example.shared

import cats.syntax.functor._
import io.circe.{ Decoder, Encoder }, io.circe.generic.auto._
import io.circe.syntax._

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

  case class table_list(tables : List[TableBase]) extends Message

  case class add_table(title: String, participants: Int) extends ITableMessage
  case class edit_table(title: String, participants: Int, update_id: Long) extends ITableMessage
  case class delete_table(title: String) extends ITableMessage


  sealed trait TableBase
  case class table(name: String, participants : Int, id: Long) extends TableBase
  case class table_deleted(title: String) extends TableBase
  object TableBase {
    implicit val encodeTable: Encoder[PublicProtocol.TableBase] = Encoder.instance {
      case table @ PublicProtocol.table(_, _, _) => table.asJson
      case td @ PublicProtocol.table_deleted(_) => td.asJson
    }

    implicit val decodeTable: Decoder[PublicProtocol.TableBase] =
      List[Decoder[PublicProtocol.TableBase]](
        Decoder[PublicProtocol.table].widen,
        Decoder[PublicProtocol.table_deleted].widen
      ).reduceLeft(_ or _)
  }
}