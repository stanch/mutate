package org.mutate

import scala.reflect.macros.Context
import scala.language.experimental.macros
import scala.language.dynamics
import scalaz.{Functor,Monoid}

/* A convenience wrapper around A ⇒ A */
case class Mutation[A](f: A ⇒ A) {
    def apply = f
}

/* For a case where `get` is missing, see `FunctorLens` */
class Lens[A, B](val get: Option[A ⇒ B], val set: A ⇒ B ⇒ A, val mod: A ⇒ (B ⇒ B) ⇒ A) extends Dynamic { self ⇒
    // composition operator
    def >=>[C](that: Lens[B, C]) = new Lens[A, C](
        get = this.get.flatMap { thisg ⇒ that.get.flatMap { thatg ⇒ Some(value ⇒ thatg(thisg(value))) }},
        set = value ⇒ field ⇒ this.mod(value)(that.set(_)(field)),
        mod = value ⇒ f ⇒ self.mod(value)(that.mod(_)(f))
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

    class Helper[CTX <: Context](val c: CTX) extends QuasiquoteCompat {
        import c.universe._

        /* Here are some quasiqoutes for lens creation */

        def fieldLens(a: Type, field: TermName, b: Type) = q"""
            org.mutate.Lens[$a, $b](
                get = value ⇒ value.$field,
                set = value ⇒ field ⇒ value.copy($field = field)
            )
        """

        def functorLens(a: TypeSymbol, b: Type) = q"""
            new org.mutate.FunctorLens[$a, $b]
        """

        def chainLens(a: Tree, b: Tree) = q"""
            $a >=> $b
        """
    }

    def selectImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: Context)(id: c.Expr[String]): c.Expr[Any] = {
        import c.universe._
        val helper = new Helper[c.type](c)

        val Literal(Constant(fieldName: String)) = id.tree
        val nextLens = fieldName match {
            case "*" ⇒ {
                /* This creates a FunctorLens */
                scala.util.Try {
                    val TypeRef(_, m, List(b)) = weakTypeOf[B]
                    helper.functorLens(m.asType, b)
                } getOrElse {
                    c.abort(c.enclosingPosition, "\"*\" requires a parametrized type")
                }
            }
            case _ ⇒ {
                /* This creates a field lens */
                scala.util.Try {
                    val NullaryMethodType(fieldType) = c.weakTypeOf[B].member(newTermName(fieldName)).typeSignatureIn(c.weakTypeOf[B])
                    helper.fieldLens(weakTypeOf[B], newTermName(fieldName), fieldType)
                } getOrElse {
                    c.abort(c.enclosingPosition, s"Invalid field: $fieldName")
                }
            }
        }
        c.Expr(helper.chainLens(c.prefix.tree, nextLens))
    }
}

/* An identity lens for starting out */
class Lenser[A] extends IdentityLens[A]

/* The main package */
object Mutate {
    class Helper[CTX <: Context](val c: CTX) extends QuasiquoteCompat {
        import c.universe._

        def applyMutation(value: Tree, m: Tree) = q"""
            $m.apply($value)
        """

        def applyToLenser(t: Tree, a: Type) = q"""
            $t.apply(new org.mutate.Lenser[$a])
        """
    }

    def mutate[A](value: A)(body: Lenser[A] ⇒ Any): A = macro mutateImpl[A]
    def mutateImpl[A: c.WeakTypeTag](c: Context)(value: c.Expr[A])(body: c.Expr[Lenser[A] ⇒ Any]): c.Expr[A] = {
        import c.universe._
        val helper = new Helper[c.type](c)

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
        val tree = (value.tree :: mutations).reduce(helper.applyMutation(_, _))

        // TODO: using defs inside `mutate{}` stopped working after introducing the closure

        // put all defs before mutations, wrap in a new closure and call, passing a Lenser
        c.Expr[A](helper.applyToLenser(Function(lenser, Block(defs, tree)), weakTypeOf[A]))
    }
}