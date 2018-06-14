package com.example.core

import com.example.shared.PublicProtocol
import org.scalatest.{Matchers, WordSpec}

class JsonModuleSpec extends WordSpec with Matchers {
  "decode" should {
    "parse ping with out of order $type & space" in {
      JsonModule.decode("""{"$type":"ping", "seq":1}""") should be(Right(PublicProtocol.ping(1)))
    }
    "parse ping with in order $type" in {
      JsonModule.decode("""{"seq":1,"$type":"ping"}""") should be(Right(PublicProtocol.ping(1)))
    }
    "parse nested tables types json without $type field" in {
      JsonModule.decode("""{"tables":[{"name":"name","participants":10,"id":1}],"$type":"table_list"}""") should
        be(Right(PublicProtocol.table_list(List(PublicProtocol.table("name", 10, 1)))))
    }
  }
  "encode" should {
    "produce pong json" in {
      JsonModule.toJson(PublicProtocol.pong(1) : PublicProtocol.Message) should be("""{"seq":1,"$type":"pong"}""")
    }
    "produce nested tables types json without $type field" in {
      JsonModule.toJson(PublicProtocol.table_list(List(PublicProtocol.table("name", 10, 1))) : PublicProtocol.Message) should
        be("""{"tables":[{"name":"name","participants":10,"id":1}],"$type":"table_list"}""")
    }
  }
}
