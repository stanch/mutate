import org.scalatest._
import org.mutate.{Lenser ⇒ $}
import org.mutate.Mutate._

class MutateSpec extends FlatSpec {
    case class A(
        a: List[Int],
        b: Int,
        c: Option[A]
    )
    case class B(
        a: String,
        b: A
    )
    val a = B("foo", A(List(4, 8), 5, Some(A(List(), 8, None))))

    "Mutate" should "work with single mutations" in {
        val b = mutate(a) { $ ⇒
            $.a := "bar"
        }
        assert(b === B("bar", A(List(4, 8), 5, Some(A(List(), 8, None)))))
    }

    it should "work with multiple mutations" in {
        val b = mutate(a) { $ ⇒
            $.a := "bar"
            $.a ~= (_ + "baz")
        }
        assert(b === B("barbaz", A(List(4, 8), 5, Some(A(List(), 8, None)))))
    }

    it should "work with several deep mutations" in {
        import scalaz.std.option._
        import scalaz.std.anyVal._

        val b = mutate(a) { $ ⇒
            $.b.b := 9
            $.b.c.*.b += 10
        }
        assert(b === B("foo", A(List(4, 8), 9, Some(A(List(), 18, None)))))
    }

//    it should "work with defs occasionally thrown in" in {
//        import scalaz.std.anyVal._
//
//        val b = mutate(a) { $ ⇒
//            val c = 5
//            $.b.b := c
//            val d = 10
//            $.b.b += d
//        }
//        assert(b === B("foo", A(List(4, 8), 15, Some(A(List(), 8, None)))))
//    }

    "The lenser" should "scrape though nested case classes" in {
        import scalaz.std.list._

        val b = mutate(a) { $ ⇒
            $.b.a += List(10)
        }
        assert(b === B("foo", A(List(4, 8, 10), 5, Some(A(List(), 8, None)))))
    }

    it should "provide a \"*\" field to get inside functors" in {
        import scalaz.std.list._
        import scalaz.std.option._
        import scalaz.std.anyVal._

        val b = mutate(a) { $ ⇒
            $.b.a.* := 9
            $.b.c.*.b += 9
        }
        assert(b === B("foo", A(List(9, 9), 5, Some(A(List(), 17, None)))))
    }

    "The lens" should "handle assignment" in {
        val b = mutate(a) { $ ⇒
            $.b.c := None
        }
        assert(b === B("foo", A(List(4, 8), 5, None)))
    }

    it should "handle modification" in {
        val b = mutate(a) { $ ⇒
            $.a ~= (_.toUpperCase())
        }
        assert(b === B("FOO", A(List(4, 8), 5, Some(A(List(), 8, None)))))
    }

    it should "handle addition for monoids" in {
        import scalaz.std.string._

        val b = mutate(a) { $ ⇒
            $.a += "’rly"
        }
        assert(b === B("foo’rly", A(List(4, 8), 5, Some(A(List(), 8, None)))))
    }

    it should "work with functors as expected" in {
        import scalaz.std.list._
        import scalaz.std.anyVal._

        val b = mutate(a) { $ ⇒
            $.b.a.* += 5
        }
        assert(b === B("foo", A(List(9, 13), 5, Some(A(List(), 8, None)))))
    }
}
