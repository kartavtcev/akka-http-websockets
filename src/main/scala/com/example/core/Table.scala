package com.example.core

import com.example.shared.PublicProtocol

object Tables {
  sealed trait TableBase
  case class TableDTO(title: String, participants: Int, updateId: Long) extends TableBase
  case class TableDeleted(title: String) extends TableBase

  def tablePrivateToPublic (privateTable : TableBase) : PublicProtocol.TableBase =
    privateTable match {
      case TableDTO(title, participants, updateId) => PublicProtocol.table(title, participants, updateId)
      case TableDeleted(title) => PublicProtocol.table_deleted(title)
    }

  def listOfPrivateTablesToPublic(tables : Vector[TableBase]) : Vector[PublicProtocol.TableBase] =
    tables.map(tablePrivateToPublic(_))
}