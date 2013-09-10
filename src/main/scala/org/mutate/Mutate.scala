/*
   Copyright 2013 Nikolay Stanchenko

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package org.mutate

import scala.reflect.macros.Context
import scala.language.experimental.macros
import scala.language.dynamics
import scalaz.{ Functor, Monoid }

/* A convenience wrapper around A ⇒ A */
case class Mutation[A](f: A ⇒ A) {
  def apply = f
}

/* For a case where `get` is missing, see `FunctorLens` */
class Lens[A, B](val get: Option[A ⇒ B], val set: A ⇒ B ⇒ A, val mod: A ⇒ (B ⇒ B) ⇒ A) extends Dynamic {
  // composition operator
  def >=>[C](that: Lens[B, C]) = new Lens[A, C](
    get = this.get.flatMap { thisg ⇒ that.get.flatMap { thatg ⇒ Some(value ⇒ thatg(thisg(value))) } },
    set = value ⇒ field ⇒ this.mod(value)(that.set(_)(field)),
    mod = value ⇒ f ⇒ this.mod(value)(that.mod(_)(f))
  )

  // compose `this` with field or functor lens
  def selectDynamic(id: String) = macro Lens.selectImpl[A, B]

  // setting mutation
  def :=(field: B) = Mutation[A](set(_)(field))
  // modifying mutation
  def ~=(f: B ⇒ B) = Mutation[A](mod(_)(f))
  // adding mutation
  def +=(field: B)(implicit monoid: Monoid[B]) = Mutation[A](mod(_)(monoid.append(_, field)))
}

/* A lens for setting or modifying a lot of things at once */
class FunctorLens[M[_], B](implicit functor: Functor[M]) extends Lens[M[B], B](
  get = None,
  set = value ⇒ field ⇒ functor.map(value)(_ ⇒ field),
  mod = value ⇒ f ⇒ functor.map(value)(f)
)

/* An identity lens */
class IdentityLens[A] extends Lens[A, A](
  get = Some(value ⇒ value),
  set = value ⇒ field ⇒ field,
  mod = value ⇒ f ⇒ f(value)
)

object Lens {
  // Lens factory
  def apply[A, B](get: A ⇒ B, set: A ⇒ B ⇒ A) = new Lens[A, B](
    get = Some(get),
    set = set,
    mod = value ⇒ f ⇒ set(value)(f(get(value)))
  )

  def selectImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: Context)(id: c.Expr[String]): c.Expr[Any] = {
    import c.universe._

    val Literal(Constant(fieldName: String)) = id.tree
    val nextLens = fieldName match {
      case "*" ⇒
        /* This creates a FunctorLens */
        scala.util.Try {
          val TypeRef(_, m, List(b)) = weakTypeOf[B]
          q"new org.mutate.FunctorLens[${m.asType}, $b]"
        } getOrElse {
          c.abort(c.enclosingPosition, "\"*\" requires a parametrized type")
        }
      case _ ⇒ {
        /* This creates a field lens */
        scala.util.Try {
          val NullaryMethodType(fieldType) = c.weakTypeOf[B].member(newTermName(fieldName)).typeSignatureIn(c.weakTypeOf[B])
          val field = newTermName(fieldName)
          q"""org.mutate.Lens[${weakTypeOf[B]}, $fieldType](
            get = value ⇒ value.$field,
            set = value ⇒ field ⇒ value.copy($field = field)
          )"""
        } getOrElse {
          c.abort(c.enclosingPosition, s"Invalid field: $fieldName")
        }
      }
    }
    c.Expr(q"${c.prefix.tree} >=> $nextLens")
  }
}

/* An identity lens for starting out */
class Lenser[A] extends IdentityLens[A]

/* The main package */
object Mutate {
  def mutate[A](value: A)(body: Lenser[A] ⇒ Any): A = macro mutateImpl[A]
  def mutateImpl[A: c.WeakTypeTag](c: Context)(value: c.Expr[A])(body: c.Expr[Lenser[A] ⇒ Any]): c.Expr[A] = {
    import c.universe._

    // extract the closure
    val Expr(Function(lenser, closure)) = body

    // find all mutations, as well as other things
    val (mutations, defs) = closure match {
      // a block of statements
      case Block(_, _) ⇒ closure.children.partition(_.tpe == weakTypeOf[Mutation[A]])
      // a single statement
      case _ ⇒ (closure :: Nil, Nil)
    }

    // build the mutation tree
    def applyMutation(value: Tree, m: Tree) = q"$m.apply($value)"
    val tree = (value.tree :: mutations).reduce(applyMutation(_, _))

    // TODO: using defs inside `mutate{}` stopped working after introducing the closure

    // put all defs before mutations, wrap in a new closure and call, passing a Lenser
    def applyToLenser(t: Tree, a: Type) = q"$t.apply(new org.mutate.Lenser[$a])"
    c.Expr[A](applyToLenser(Function(lenser, Block(defs, tree)), weakTypeOf[A]))
  }
}