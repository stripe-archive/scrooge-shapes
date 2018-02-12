# Scrooge Shapes

[![Build status](https://img.shields.io/travis/stripe/scrooge-shapes/master.svg)](https://travis-ci.org/stripe/scrooge-shapes)
[![Coverage status](https://img.shields.io/codecov/c/github/stripe/scrooge-shapes/master.svg)](https://codecov.io/github/stripe/scrooge-shapes)
[![Maven Central](https://img.shields.io/maven-central/v/com.stripe/scrooge-shapes_2.12.svg)](https://maven-badges.herokuapp.com/maven-central/com.stripe/scrooge-shapes_2.12)

This project provides instances of [Shapeless][shapeless]'s `Generic` type classes for types
generated for [Apache Thrift][thrift] definitions by Twitter's [Scrooge][scrooge].

These instances allow you to use the generic derivation mechanisms provided by libraries like
[Circe][circe] or [scalacheck-shapeless][scalacheck-shapeless] to generate boilerplate-free
type class instances for your Scrooge types.

## History

This library is based on a [demonstration project][shapes-demo] written by
[Travis Brown][travisbrown] in 2016, and is now used internally at Stripe to support generic
derivation and other applications.

## Usage

Given a Thrift definition like the following, Scrooge will generate a Scala type that is
case class-like, but not a case class:

```thrift
namespace java com.stripe.scrooge.shapes

struct MapExample {
  1: required string id
  3: optional map<string,string> metadata
}
```

Shapeless cannot derive `Generic` or `LabelledGeneric` instances for types like this on its own,
which is where Shapes comes in—you simply import the contents of the package object and then you can
use any generic derivation mechanism as if you were working with ordinary case classes or sealed
trait hierarchies.

```scala
scala> import com.stripe.scrooge.shapes._
import com.stripe.scrooge.shapes._

scala> import io.circe.ObjectEncoder, io.circe.syntax._
import io.circe.ObjectEncoder
import io.circe.syntax._

scala> import io.circe.generic.semiauto.deriveEncoder
import io.circe.generic.semiauto.deriveEncoder

scala> implicit val encodeMapExample: ObjectEncoder[MapExample] = deriveEncoder
encodeMapExample: io.circe.ObjectEncoder[com.stripe.scrooge.shapes.MapExample] = io.circe.generic.encoding.DerivedObjectEncoder$$anon$1@2ecb4528

scala> MapExample("abc", Some(Map("foo" -> "bar", "baz" -> "quz"))).asJson
res0: io.circe.Json =
{
  "id" : "abc",
  "metadata" : {
    "foo" : "bar",
    "baz" : "quz"
  }
}
```

See the `GenericInstancesTest` test suite for more details about the representations used for these
types.

## License

scrooge-shapes is licensed under the **[Apache License, Version 2.0][apache]**
(the "License"); you may not use this software except in compliance with the
License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[apache]: http://www.apache.org/licenses/LICENSE-2.0
[circe]: https://github.com/circe/circe
[scalacheck-shapeless]: https://github.com/alexarchambault/scalacheck-shapeless
[scrooge]: https://github.com/twitter/scrooge
[shapeless]: https://github.com/milessabin/shapeless
[shapes-demo]: https://github.com/travisbrown/scrooge-circe-demo
[thrift]: https://thrift.apache.org
[travisbrown]: https://github.com/travisbrown
