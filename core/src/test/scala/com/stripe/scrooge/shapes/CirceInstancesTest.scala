package com.stripe.scrooge.shapes

import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.syntax._
import org.scalacheck.Prop
import org.scalatest.FunSuite
import org.scalatest.prop.Checkers

class CirceInstancesTest extends FunSuite {
  test("circe should derive instances for Scrooge structs with map members") {
    import io.circe.generic.auto._

    Checkers.check(
      Prop.forAll { (id: String, metadata: Option[Map[String, String]]) =>
        val ex = MapExample(id, metadata)
        val exJson = ex.asJson
        val exDecoder = Decoder[MapExample].decodeJson(exJson)

        exDecoder === Right(ex)
      }
    )
  }

  test("circe-generic-extras should derive instances for Scrooge structs") {
    import io.circe.generic.extras.auto._

    implicit val circeConfig: Configuration = Configuration.default

    Checkers.check(
      Prop.forAll { (id: String, metadata: Option[Map[String, String]]) =>
        val ex = MapExample(id, metadata)
        val exJson = ex.asJson
        val exDecoder = Decoder[MapExample].decodeJson(exJson)

        exDecoder === Right(ex)
      }
    )
  }
}
