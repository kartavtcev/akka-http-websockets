package com.example.core

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.syntax._

import com.example.shared.PublicProtocol

object JsonModule {
  implicit val genDevConfig: Configuration = Configuration.default.withDiscriminator("$type")

  def decode(str : String) : scala.Either[io.circe.Error, PublicProtocol.Message] = io.circe.parser.decode[PublicProtocol.Message](str)
  def toJson(msg : PublicProtocol.Message) = msg.asJson.noSpaces
}
