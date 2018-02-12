package com.stripe.scrooge.shapes

import com.twitter.scrooge.TFieldBlob
import org.apache.thrift.protocol.TField
import org.scalacheck.{Arbitrary, Gen, Prop}
import org.scalatest.FunSuite
import org.scalatest.prop.Checkers
import shapeless._
import shapeless.record.Record
import shapeless.union.Union

trait ArbitraryInstances {
  implicit def arbitraryHNil: Arbitrary[HNil] = Arbitrary(Arbitrary.arbitrary[Unit].map(_ => HNil))
  implicit def arbitraryHCons[H: Arbitrary, T <: HList: Arbitrary]: Arbitrary[H :: T] = Arbitrary(
    for {
      h <- Arbitrary.arbitrary[H]
      t <- Arbitrary.arbitrary[T]
    } yield h :: t
  )

  implicit def arbitrarySingletonCCons[L: Arbitrary]: Arbitrary[L :+: CNil] =
    Arbitrary(Arbitrary.arbitrary[L].map(Inl(_)))

  implicit def arbitraryCCons[L: Arbitrary, R <: Coproduct: Arbitrary]: Arbitrary[L :+: R] =
    Arbitrary(
      Gen.oneOf(Arbitrary.arbitrary[L].map(Inl(_)), Arbitrary.arbitrary[R].map(Inr(_)))
    )

  implicit def arbitraryTFieldBlob: Arbitrary[TFieldBlob] =
    Arbitrary(Gen.const(TFieldBlob(new TField(), Array.empty[Byte])))

  implicit def arbitraryStructExample: Arbitrary[StructExample] =
    Arbitrary(
      for {
        foo <- Arbitrary.arbitrary[String]
        bar <- Arbitrary.arbitrary[Option[String]]
      } yield StructExample(foo, bar)
    )

  implicit def arbitraryNestedStructExample: Arbitrary[NestedStructExample] =
    Arbitrary(
      for {
        str <- Arbitrary.arbitrary[StructExample]
        qux <- Arbitrary.arbitrary[Option[Long]]
      } yield NestedStructExample(str, qux)
    )

  implicit def arbitraryUnionExample: Arbitrary[UnionExample] =
    Arbitrary(
      Gen.oneOf(
        Arbitrary.arbitrary[StructExample].map(UnionExample.A(_)),
        Arbitrary.arbitrary[Int].map(UnionExample.B(_)),
        Arbitrary.arbitrary[String].map(UnionExample.C(_))
      )
    )
}

class GenericInstancesTest extends FunSuite with ArbitraryInstances {
  def roundTripGeneric[A: Arbitrary, Repr](gen: Generic.Aux[A, Repr]): Prop =
    Prop.forAll { (a: A) =>
      gen.from(gen.to(a)) === a
    }

  def roundTripLabelledGeneric[A: Arbitrary, Repr](gen: LabelledGeneric.Aux[A, Repr]): Prop =
    Prop.forAll { (a: A) =>
      gen.from(gen.to(a)) === a
    }

  test("Structs should round-trip through Generic") {
    Checkers.check(roundTripGeneric(Generic[StructExample]))
  }

  test("Structs should have the expected Generic representations") {
    Checkers.check(
      Prop.forAll { (s: StructExample) =>
        val gen = Generic[StructExample]
        val repr = gen.to(s)

        shapeless.test.typed[String :: Option[String] :: HNil](repr)
        repr === s.foo :: s.bar :: HNil
      }
    )
  }

  test("Nested structs should round-trip through Generic") {
    Checkers.check(roundTripGeneric(Generic[NestedStructExample]))
  }

  test("Nested structs should have the expected Generic representations") {
    Checkers.check(
      Prop.forAll { (s: NestedStructExample) =>
        val gen = Generic[NestedStructExample]
        val repr = gen.to(s)

        shapeless.test.typed[StructExample :: Option[Long] :: HNil](repr)
        repr === s.str :: s.qux :: HNil
      }
    )
  }

  test("Unions should round-trip through Generic") {
    Checkers.check(roundTripGeneric(Generic[UnionExample]))
  }

  test("Unions should have the expected Generic representations") {
    Checkers.check(
      Prop.forAll { (u: UnionExample) =>
        val gen = Generic[UnionExample]
        val repr = gen.to(u)

        shapeless.test.typed[StructExample :+: Int :+: String :+: TFieldBlob :+: CNil](repr)

        u match {
          case UnionExample.A(s) => Inl(s) === repr
          case UnionExample.B(b) => Inr(Inl(b)) === repr
          case UnionExample.C(c) => Inr(Inr(Inl(c))) === repr
          case UnionExample.UnknownUnionField(_) => false
        }
      }
    )
  }

  test("Structs should round-trip through LabelledGeneric") {
    Checkers.check(roundTripLabelledGeneric(LabelledGeneric[StructExample]))
  }

  test("Structs should have the expected LabelledGeneric representations") {
    Checkers.check(
      Prop.forAll { (s: StructExample) =>
        val gen = LabelledGeneric[StructExample]
        val repr = gen.to(s)

        shapeless.test.typed[Record.`'foo -> String, 'bar -> Option[String]`.T](repr)
        repr === s.foo :: s.bar :: HNil
      }
    )
  }

  test("Nested structs should round-trip through LabelledGeneric") {
    Checkers.check(roundTripLabelledGeneric(LabelledGeneric[NestedStructExample]))
  }

  test("Nested structs should have the expected LabelledGeneric representations") {
    Checkers.check(
      Prop.forAll { (s: NestedStructExample) =>
        val gen = LabelledGeneric[NestedStructExample]
        val repr = gen.to(s)

        shapeless.test.typed[Record.`'str -> StructExample, 'qux -> Option[Long]`.T](repr)
        repr === s.str :: s.qux :: HNil
      }
    )
  }

  test("Unions should round-trip through LabelledGeneric") {
    Checkers.check(roundTripLabelledGeneric(LabelledGeneric[UnionExample]))
  }

  test("Unions should have the expected LabelledGeneric representations") {
    Checkers.check(
      Prop.forAll { (u: UnionExample) =>
        val gen = LabelledGeneric[UnionExample]
        val repr = gen.to(u)

        shapeless.test.typed[
          Union.`'A -> StructExample, 'B -> Int, 'C -> String, 'UnknownUnionField -> TFieldBlob`.T
        ](repr)

        u match {
          case UnionExample.A(s) => Inl(s) === repr
          case UnionExample.B(b) => Inr(Inl(b)) === repr
          case UnionExample.C(c) => Inr(Inr(Inl(c))) === repr
          case UnionExample.UnknownUnionField(_) => false
        }
      }
    )
  }
}
