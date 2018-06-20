package com.example.shared

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
        be(Right(PublicProtocol.table_list(List(PublicProtocol.table(Some(1), "name", 10)))))
    }

    "parse nested table type json without $type field, while null field Id is skipped" in {
      JsonModule.decode("""{
                            "$type": "add_table",
                            "after_id": 1,
                            "table": {
                              "name": "table - Foo Fighters",
                              "participants": 4
                            }
                          }""") should
        be(Right(PublicProtocol.add_table(1, PublicProtocol.table(None, "table - Foo Fighters", 4))))
    }
  }
  "encode" should {
    "produce pong json" in {
      JsonModule.toJson(PublicProtocol.pong(1) : PublicProtocol.Message) should be("""{"seq":1,"$type":"pong"}""")
    }
    "produce nested tables types json without $type field" in {
      JsonModule.toJson(PublicProtocol.table_list(List(PublicProtocol.table(Some(1), "name", 10))) : PublicProtocol.Message) should
        be("""{"tables":[{"id":1,"name":"name","participants":10}],"$type":"table_list"}""")
    }

    "produce nested table type json without $type field, skip null field Id" in {
      JsonModule.toJson(PublicProtocol.add_table(1, PublicProtocol.table(None, "table - Foo Fighters", 4)) : PublicProtocol.Message) should
        be("""{"after_id":1,"table":{"name":"table - Foo Fighters","participants":4},"$type":"add_table"}""")
    }
  }
}
