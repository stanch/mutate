### *Mutate* — an easy way to “mutate” nested immutable structures

Suppose we have two case classes:
```scala
case class A(
    a: List[Int],
    b: Int,
    c: Option[A]
)
case class B(
    a: String,
    b: A
)
```
and a value
```scala
val a = B("foo", A(List(4, 8), 5, Some(A(List(), 8, None))))
```
Mutate lets us produce a copy of ```a``` with a particular field changed,
seamlessly navigating through all the nested case class debris:
```scala
import scalaz.std.option._
import scalaz.std.anyVal._

val b = mutate(a) { $ ⇒
    $.b.b := 9
    $.b.c.*.b += 10
}
assert(b === B("foo", A(List(4, 8), 9, Some(A(List(), 18, None)))))
```
### Wait, what?

* ```mutate``` is a macro that parses its second argument and searches for anything of type ```Mutation[A]```, which is
a simple wrapper around ```A ⇒ A```. The result is all mutations applied to the original value in order of their appearance.
* The ```$```, passed to the closure, can be used to create lenses at will. ```*``` is a special field that builds
a lens going inside functors like List or Option. Did I mention there was no runtime reflexion involved?
* The syntax is inspired by ```.sbt``` files. You create mutations, using lens’ methods ```:=``` (set a value),
```~=``` (modify a value) and ```+=``` (add to value, works for scalaz monoids).
* Of course you can use any other lens library, or even dispense with lens completely, as long as you create ```Mutation```s.

### More examples
```scala
val b = mutate(a) { $ ⇒
    $.a := "bar"
    $.a ~= (_ + "baz")
}
assert(b === B("barbaz", A(List(4, 8), 5, Some(A(List(), 8, None)))))
```
```scala
import scalaz.std.list._
import scalaz.std.option._
import scalaz.std.anyVal._

val b = mutate(a) { $ ⇒
    $.b.a.* := 9
    $.b.c.*.b += 9
}
assert(b === B("foo", A(List(9, 9), 5, Some(A(List(), 17, None)))))
```
You can find even more examples in the tests.

### Usage

Install to local ivy repo:
```
git clone https://github.com/stanch/mutate.git
cd mutate && sbt publish-local
```
To use:
```
libraryDependencies += "me.stanch" %% "mutate" % "0.1-SNAPSHOT"
```
To run tests:
```
cd mutate
sbt
project tests
test
```

### Current limitations

* The support for defs and other stuff inside ```mutate``` is broken and hopefully will be fixed soon.
* So far there is no syntax for using ```$``` to get values, e.g. to reference one field in another.

### TODO

* See above
* Better docs
* Perhaps create a maven repo

### Acknowledgements

**Mutate** is inspired by [*sbt*](http://www.scala-sbt.org/release/docs/Getting-Started/Basic-Def.html#how-build-sbt-defines-settings) and [*rillit*](https://github.com/akisaarinen/rillit). Quasiquotes compatibility layer
by @xeno_by is taken from here: https://github.com/scalamacros/sbt-example-paradise210.
