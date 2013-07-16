package org.mutate

import org.scalatest._
import org.mutate.Mutate._
import spray.json._
import DefaultJsonProtocol._
import spray.json.lenses.JsonLenses._
import org.mutate.JsonLensesSupport._

class JsonLensesSupportSpec extends FlatSpec {
    val json = """{"a": {"c": 9}, "b": "foo"}""".asJson

    "Json lenses support" should "work with `set`" in {
        val upd = mutate(json) { $ ⇒
            'a / 'c := 20
        }
        assert(upd === """{"a": {"c": 20}, "b": "foo"}""".asJson)
    }

    it should "work with `update`" in {
        val upd = mutate(json) { $ ⇒
            field("b") ~= {x: String ⇒ x + "bar"}
        }
        assert(upd === """{"a": {"c": 9}, "b": "foobar"}""".asJson)
    }

    val json2 = """{"a": [1, 2, 3], "b": "foo"}""".asJson
    it should "work with sequences" in {
        val upd = mutate(json2) { $ ⇒
            field("a") / * ~= {x: Int ⇒ x + 1}
        }
        assert(upd === """{"a": [2, 3, 4], "b": "foo"}""".asJson)
    }
}
