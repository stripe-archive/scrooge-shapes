package com.stripe.scrooge.shapes

import scala.reflect.macros.whitebox
import shapeless.{Annotations, Default, DefaultSymbolicLabelling, Generic, HNil}

class GenericMacros(val c: whitebox.Context) {
  import c.universe._

  sealed abstract class Named(name: Name) {
    private[this] val nameType: ConstantType = internal.constantType(Constant(decodedString))

    final def decodedString: String = name.decodedName.toString
    final def labelType: CompoundTypeTree =
      tq"_root_.scala.Symbol with _root_.shapeless.tag.Tagged[$nameType]"
  }

  case class StructMember(name: TermName, valueType: Type) extends Named(name)

  object StructMember {
    def of[A: WeakTypeTag]: List[StructMember] = {
      val companionApply = c.weakTypeOf[A].companion.decl(TermName("apply")).asMethod

      companionApply.paramLists.head.map { param =>
        StructMember(param.asTerm.name, param.asTerm.info)
      }.toList
    }
  }

  case class UnionConstructor(name: TypeName, valueType: Type, valueCompanion: TermName)
      extends Named(name)

  object UnionConstructor {
    def of[A: WeakTypeTag]: List[UnionConstructor] =
      c.weakTypeOf[A]
        .companion
        .decls
        .collect {
          case sym if sym.isClass && sym.asClass.isCaseClass => sym.asClass
        }
        .map { sym =>
          UnionConstructor(
            sym.name,
            sym.primaryConstructor.asMethod.paramLists.head.head.asTerm.info.dealias,
            sym.companion.name.toTermName
          )
        }
        .toList
  }

  private[this] def isUnion[A: WeakTypeTag]: Option[Boolean] = {
    val baseNames = c.weakTypeOf[A].baseClasses.tail.map(_.fullName)

    if (baseNames.contains("com.twitter.scrooge.ThriftStruct")) {
      Some(baseNames.contains("com.twitter.scrooge.ThriftUnion"))
    } else None
  }

  def materializeGeneric[A: WeakTypeTag]: c.Expr[Generic[A]] =
    isUnion[A] match {
      case Some(true) => materializeUnionGeneric[A]
      case Some(false) => materializeStructGeneric[A]
      case None =>
        c.abort(
          c.enclosingPosition,
          s"Cannot derive a Generic instance for ${c.weakTypeOf[A]}"
        )
    }

  def materializeDefaultSymbolicLabelling[A: WeakTypeTag]: c.Expr[DefaultSymbolicLabelling[A]] =
    isUnion[A] match {
      case Some(true) => materializeUnionDefaultSymbolicLabelling[A]
      case Some(false) => materializeStructDefaultSymbolicLabelling[A]
      case None =>
        c.abort(
          c.enclosingPosition,
          s"Cannot derive a DefaultSymbolLabelling instance for ${c.weakTypeOf[A]}"
        )
    }

  private[this] def createGeneric(A: Type, R: Tree, toResult: Tree, fromResult: Tree): Tree =
    q"""
      new _root_.shapeless.Generic[$A] {
        type Repr = $R
        def to(a: $A): Repr = $toResult
        def from(r: Repr): $A = $fromResult
      }: _root_.shapeless.Generic.Aux[$A, $R]
    """

  private[this] def createDefaultSymbolicLabelling(A: Type, names: List[Named]): Tree = {
    val L = names.foldRight[Tree](tq"_root_.shapeless.HNil") {
      case (name, acc) => tq"_root_.shapeless.::[${name.labelType}, $acc]"
    }

    val labels = names.foldRight[Tree](q"_root_.shapeless.HNil") {
      case (name, acc) =>
        q"_root_.shapeless.::(_root_.scala.Symbol(${name.decodedString}).asInstanceOf[${name.labelType}], $acc)"
    }

    q"""
      new _root_.shapeless.DefaultSymbolicLabelling[$A] {
        type Out = $L
        def apply(): $L = $labels
      }: _root_.shapeless.DefaultSymbolicLabelling.Aux[$A, $L]
    """
  }

  private[this] def createDefault(A: Type, names: List[Named]): Tree = {
    val L = names.foldRight[Tree](tq"_root_.shapeless.HNil") {
      case (name, acc) => tq"_root_.shapeless.::[_root_.scala.None.type, $acc]"
    }

    val value = names.foldRight[Tree](q"_root_.shapeless.HNil") {
      case (name, acc) =>
        q"_root_.shapeless.::(_root_.scala.None, $acc)"
    }

    q"_root_.shapeless.Default.mkDefault[$A, $L]($value): _root_.shapeless.Default.Aux[$A, $L]"
  }

  private[this] def createAnnotations(A: Type, T: Type): Tree =
    q"""
      _root_.shapeless.Annotations.mkAnnotations[$A, $T, _root_.shapeless.HNil](
        _root_.shapeless.HNil: _root_.shapeless.HNil
      ): _root_.shapeless.Annotations.Aux[$A, $T, _root_.shapeless.HNil]
    """

  def materializeStructGeneric[A: WeakTypeTag]: c.Expr[Generic[A]] = {
    val members = StructMember.of[A]

    val A = c.weakTypeOf[A]
    val R = members.foldRight[Tree](tq"_root_.shapeless.HNil") {
      case (member, acc) => tq"_root_.shapeless.::[${member.valueType}, $acc]"
    }

    val toResult = members.foldRight[Tree](q"_root_.shapeless.HNil") {
      case (member, acc) => q"_root_.shapeless.::(a.${member.name}, $acc)"
    }

    val companionApply = A.companion.decl(TermName("apply")).asMethod
    val elementNames = (1 to members.size).map(i => TermName(s"e$i"))

    val fromResult = elementNames.foldRight[Tree](q"$companionApply(...${List(elementNames)})") {
      case (h, acc) => q"{ r match { case _root_.shapeless.::($h, r) => $acc } }"
    }

    val tree = createGeneric(A, R, toResult, fromResult)
    c.Expr[Generic[A]](tree)
  }

  def materializeUnionGeneric[A: WeakTypeTag]: c.Expr[Generic[A]] = {
    val constructors: List[UnionConstructor] = UnionConstructor.of[A]

    val A = c.weakTypeOf[A]
    val C = A.typeSymbol.companion
    val R = constructors.foldRight[Tree](tq"_root_.shapeless.CNil") {
      case (constructor, acc) => tq"_root_.shapeless.:+:[${constructor.valueType}, $acc]"
    }

    val toResultCases = constructors.map { constructor =>
      val binding = TermName(c.freshName())
      cq"""
        $C.${constructor.valueCompanion}($binding) => _root_.shapeless.Coproduct[$R](
          $binding.asInstanceOf[${constructor.valueType}]
        )
      """
    }

    val toResult = q"a match { case ..$toResultCases }"

    val fromResult = constructors.foldRight(
      q"r match { case l => l.impossible }"
    ) {
      case (constructor, acc) => q"""
        r match {
          case _root_.shapeless.Inl(l) => $C.${constructor.valueCompanion}(l)
          case _root_.shapeless.Inr(r) => $acc
        }
      """
    }

    val tree = createGeneric(A, R, toResult, fromResult)
    c.Expr[Generic[A]](tree)
  }

  def materializeStructDefaultSymbolicLabelling[A: WeakTypeTag]
    : c.Expr[DefaultSymbolicLabelling[A]] = {
    val members = StructMember.of[A]

    val tree = createDefaultSymbolicLabelling(c.weakTypeOf[A], members)
    c.Expr[DefaultSymbolicLabelling[A]](tree)
  }

  def materializeUnionDefaultSymbolicLabelling[A: WeakTypeTag]
    : c.Expr[DefaultSymbolicLabelling[A]] = {
    val constructors = UnionConstructor.of[A]

    val tree = createDefaultSymbolicLabelling(c.weakTypeOf[A], constructors)
    c.Expr[DefaultSymbolicLabelling[A]](tree)
  }

  def materializeDefault[A: WeakTypeTag]: c.Expr[Default[A]] =
    isUnion[A] match {
      case Some(false) =>
        val members = StructMember.of[A]

        val tree = createDefault(c.weakTypeOf[A], members)
        c.Expr[Default[A]](tree)
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Cannot derive a Default instance for ${c.weakTypeOf[A]}"
        )
    }

  def materializeAnnotations[A: WeakTypeTag, T: WeakTypeTag]: c.Expr[Annotations.Aux[A, T, HNil]] =
    isUnion[T] match {
      case Some(false) =>
        val tree = createAnnotations(c.weakTypeOf[A], c.weakTypeOf[T])
        c.Expr[Annotations.Aux[A, T, HNil]](tree)
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Cannot derive an Annotations instance for ${c.weakTypeOf[A]}"
        )
    }
}
