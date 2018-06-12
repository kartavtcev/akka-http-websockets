package com.example.core

object Tables {
  sealed trait TableBase
  case class TableDTO(title: String, participants: Int, updateId: Long) extends TableBase
  case class TableDeleted(title: String) extends TableBase
}