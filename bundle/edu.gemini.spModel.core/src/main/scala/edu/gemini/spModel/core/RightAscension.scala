package edu.gemini.spModel.core

import scalaz._, Scalaz._

/** Newtype for `Angle`, tagging it as a right ascension. */
sealed trait RightAscension {

  /** 
   * This `RightAscension` as an untagged `Angle`. 
   * @group Conversions
   */
  def toAngle: Angle
  
  /**
   * Offset this `RightAscension` by the given angle.
   * @group Operations
   */
  def offset(a: Angle): RightAscension =
    RightAscension.fromAngle(toAngle + a)

  /** @group Overrides */
  override final def toString = 
    f"RA($toAngle)"

  /** @group Overrides */
  override final def equals(a: Any) =
    a match {
      case ra: RightAscension => ra.toAngle == this.toAngle
      case _ => false
    }

  /** @group Overrides */
  override final def hashCode =
    toAngle.hashCode

}

object RightAscension {

  /** 
   * Construct a `RightAscension` from an `Angle`.
   * @group Constructors
   */
  def fromAngle(a: Angle): RightAscension =
    new RightAscension {
      val toAngle = a
    }

  /** 
   * The `RightAscension` at zero degrees.
   * @group Constructors
   */
  val zero: RightAscension = 
    fromAngle(Angle.zero)

  /** @group Typeclass Instances */
  implicit val RightAscensionOrder: Order[RightAscension] =
    Order.orderBy(_.toAngle)

  /** @group Typeclass Instances */
  implicit val RightAscensionOrdering: scala.Ordering[RightAscension] =
    scala.Ordering.by(_.toAngle)

}



