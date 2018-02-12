package com.stripe.scrooge.shapes

import scala.language.experimental.macros
import shapeless.{Annotations, Default, DefaultSymbolicLabelling, Generic, HNil}

trait GenericInstances {
  implicit def deriveGeneric[A]: Generic[A] = macro GenericMacros.materializeGeneric[A]
  implicit def deriveDefaultSymbolicLabelling[A]: DefaultSymbolicLabelling[A] =
    macro GenericMacros.materializeDefaultSymbolicLabelling[A]

  /**
   * Derive an empty default case class values type class instance.
   *
   * @note We do not currently provide the default values from the Scrooge
   * struct definitions. This could be supported in a future release, but is
   * non-trivial and not necessary for any of our current use cases.
   */
  implicit def deriveDefault[A]: Default[A] = macro GenericMacros.materializeDefault[A]

  /**
   * Derive an empty annotations type class instance.
   *
   * @note We do not currently support annotation values for Scrooge struct
   * definitions.
   */
  implicit def deriveAnnotations[A, T]: Annotations.Aux[A, T, HNil] =
    macro GenericMacros.materializeAnnotations[A, T]
}