package com.example.shared

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.syntax._

object JsonModule {
  implicit val genDevConfig: Configuration = Configuration.default.withDiscriminator("$type")

  def decode(str : String) : scala.Either[io.circe.Error, PublicProtocol.Message] = io.circe.parser.decode[PublicProtocol.Message](str)
  def toJson(msg : PublicProtocol.Message) = msg.asJson.noSpaces
}
