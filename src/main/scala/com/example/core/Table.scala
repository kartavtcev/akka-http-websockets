package com.example.core

import scala.collection.immutable.Seq
import com.example.shared.PublicProtocol

// separate Table type is not required here. It's for 2 reasons:
// 1. Not to use current PublicProtocol case classes naming (may be could be fixed with circe @annonations)
// 2. Having data domain models conversions   to/from   API models, aka production project.
object Tables {
  sealed trait TableBase
  case class TableDTO(id: Int, title: String, participants: Int) extends TableBase
  //case class TableDeleted(title: String) extends TableBase

  def privateToPublic(privateTable : TableBase) : PublicProtocol.TableBase =
    privateTable match {
      case TableDTO(id, title, participants) => PublicProtocol.table(Some(id), title, participants)
      //case TableDeleted(title) => PublicProtocol.table_deleted(title)
    }

  def publicToPrivate(publicTable : PublicProtocol.table) : TableBase = {
    publicTable match {
      case PublicProtocol.table(Some(id), title, participants) => TableDTO(id, title, participants)
    }
  }

  def listOfPrivateTablesToPublic(tables : Seq[TableBase]) : Seq[PublicProtocol.TableBase] =
    tables.map(privateToPublic(_))
}