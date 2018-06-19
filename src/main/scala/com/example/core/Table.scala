package com.example.core

import scala.collection.immutable.Seq
import com.example.shared.PublicProtocol

// separate Table type is not required here. It's for 2 reasons:
// 1. Not to use current PublicProtocol case classes naming (may be could be fixed with circe @annonations)
// 2. Having data domain models conversions   to/from   API models, aka production project.
object Tables {
    //sealed trait TableBase
  case class Table(id: Int, title: String, participants: Int) //extends TableBase
  //case class TableDeleted(title: String) extends TableBase

  def privateToPublic(privateTable : Table) : PublicProtocol.table =
    privateTable match {
      case Table(id, title, participants) => PublicProtocol.table(Some(id), title, participants)
    }

  def publicToPrivate(publicTable : PublicProtocol.table, newId : Int = -1) : Table = {
    publicTable match {
      case PublicProtocol.table(Some(id), title, participants) => Table(id, title, participants)
      case PublicProtocol.table(None, title, participants) => Table(newId, title, participants)
    }
  }

  def listOfPrivateTablesToPublic(tables : Seq[Table]) : Seq[PublicProtocol.table] =
    tables.map(privateToPublic(_))
}