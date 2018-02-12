# Scrooge Shapes

[![Build status](https://img.shields.io/travis/stripe/scrooge-shapes/master.svg)](https://travis-ci.org/stripe/scrooge-shapes)
[![Coverage status](https://img.shields.io/codecov/c/github/stripe/scrooge-shapes/master.svg)](https://codecov.io/github/stripe/scrooge-shapes)
[![Maven Central](https://img.shields.io/maven-central/v/com.stripe/scrooge-shapes_2.12.svg)](https://maven-badges.herokuapp.com/maven-central/com.stripe/scrooge-shapes_2.12)

This project provides instances of [Shapeless][shapeless]'s `Generic` type classes for types
generated for [Apache Thrift][thrift] definitions by Twitter's [Scrooge][scrooge].

These instances allow you to use the generic derivation mechanisms provided by libraries like
[Circe][circe] or [scalacheck-shapeless][scalacheck-shapeless] to generate boilerplate-free
type class instances for your Scrooge types.

Note that this library does not bring its own runtime dependency on either Scrooge or
[libthrift][thrift], and the macros provided here have been used on a range of Scrooge versions,
from 4.6.0 to 18.2.0. Currently only a single (recent) Scrooge version is being tested and
supported, but this may change in the future.

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
types. In particular note that if you're working with a Thrift union, you may need to provide
instances of your target type class for `TFieldBlob` explicitly. For example, if you want to want to
use Circe's generic derivation with a union like this:

```thrift
namespace java com.stripe.scrooge.shapes

struct StructExample {
  1: required string foo
  2: optional string bar
}

union UnionExample {
  1: StructExample a
  2: i32 b
  3: string c
}
```

You will need some utility code like the following:

```scala
import io.circe.Encoder, io.circe.scodec._, io.circe.syntax._
import com.twitter.scrooge.TFieldBlob, scodec.bits.ByteVector

implicit val encodeTFieldBlob: Encoder[TFieldBlob] =
  Encoder.instance(blob => ByteVector(blob.data).asJson)
```

This will allow you to derive `Encoder` and `Decoder` types for `UnionExample`:

```scala
import com.stripe.scrooge.shapes._, io.circe.generic.auto._

scala> val ex: UnionExample = UnionExample.A(StructExample("abc", Some("123")))
ex: com.stripe.scrooge.shapes.UnionExample = A(StructExample(abc,Some(123)))

scala> ex.asJson
res0: io.circe.Json =
{
  "A" : {
    "foo" : "abc",
    "bar" : "123"
  }
}
```

Without the `TFieldBlob` encoder, Circe would not know how to encode the
`UnknownUnionField` disjunct that is included in Scrooge's representation of
every union, and its generic derivation would fail to compile.

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
