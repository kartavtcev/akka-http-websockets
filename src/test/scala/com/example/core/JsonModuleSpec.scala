package com.example.core

import com.example.shared.PublicProtocol
import org.scalatest.{Matchers, WordSpec}

class JsonModuleSpec extends WordSpec with Matchers {
  "decode" should {
    "parse ping" in {
      JsonModule.decode("""{"seq":1,"$type":"ping"}""") should be(Right(PublicProtocol.ping(1)))
    }
  }
  "encode" should {
    "produce pong json" in {
      JsonModule.toJson(PublicProtocol.pong(1) : PublicProtocol.Message) should be("""{"seq":1,"$type":"pong"}""")
    }
  }
}
