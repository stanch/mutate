package org.mutate

import spray.json.lenses.JsonLenses._
import spray.json.{ JsonFormat, JsValue }

object JsonLensesSupport {
  implicit class RichJsonLens[A[_]](lens: spray.json.lenses.Lens[A]) {
    def :=[B: JsonFormat](v: B) = Mutation {
      x: JsValue ⇒ x.update(lens ! set(v))
    }

    def ~=[B: JsonFormat](f: B ⇒ B) = Mutation {
      x: JsValue ⇒ x.update(lens ! modify(f))
    }
  }
}
