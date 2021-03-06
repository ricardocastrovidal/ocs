package edu.gemini.spModel.inst

import java.awt.Shape
import java.awt.geom.Point2D

import edu.gemini.pot.ModelConverters._
import edu.gemini.shared.util.immutable.{DefaultImList, ImList, Option => GOption}
import edu.gemini.skycalc.Offset
import edu.gemini.spModel.core.Target.SiderealTarget
import edu.gemini.spModel.core._
import edu.gemini.spModel.guide.GuideProbe
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.rich.shared.immutable._
import edu.gemini.spModel.target.SPTarget

import scala.collection.JavaConverters._

/**
 * Geometry (represented by a list of shapes) for a guide probe arm.
 */
trait ProbeArmGeometry {
  /**
   * Create a list of Shape representing the probe arm and all its components (e.g. pickoff mirror).
   * @return the list of shapes
   */
  def geometry: List[Shape]

  def geometryAsJava: ImList[Shape] =
    DefaultImList.create(geometry.asJava)

  /**
   * An instance of the probe being represented by this class.
   * @return the probe instance
   */
  protected def guideProbeInstance: GuideProbe

  /**
   * For a given context, guide star coordinates, and offset, calculate the arm adjustment that will be used for the
   * guide star at those coordinates.
   * @param ctx             the context representing the configuration
   * @param guideStarCoords the guide star for which to calculate the adjustment
   * @param offset          the offset for which to calculate the adjustment
   * @return                the probe arm adjustments for this data
   */
  def armAdjustment(ctx: ObsContext, guideStarCoords: Coordinates, offset: Offset): Option[ArmAdjustment]

  def armAdjustment(ctx: ObsContext, guideStar: SPTarget, offset: Offset): Option[ArmAdjustment] =
    armAdjustment(ctx, guideStar.getTarget.getSkycalcCoordinates.toNewModel, offset)

  def armAdjustment(ctx: ObsContext, guideStar: SiderealTarget, offset: Offset): Option[ArmAdjustment] =
    armAdjustment(ctx, guideStar.coordinates, offset)

  def armAdjustmentAsJava(ctx: ObsContext, guideStarCoords: Coordinates, offset: Offset): GOption[ArmAdjustment] =
    armAdjustment(ctx, guideStarCoords, offset).asGeminiOpt

  def armAdjustmentAsJava(ctx: ObsContext, guideStar: SPTarget, offset: Offset): GOption[ArmAdjustment] =
    armAdjustment(ctx, guideStar, offset).asGeminiOpt

  def armAdjustmentAsJava(ctx: ObsContext, guideStar: SiderealTarget, offset: Offset): GOption[ArmAdjustment] =
    armAdjustment(ctx, guideStar, offset).asGeminiOpt

  /**
   * For a given context and offset, calculate the arm adjustment that will be used for the primary / selected guide
   * star.
   * @param ctx    the context representing the configuration
   * @param offset the offset for which to calculate the adjustment
   * @return       the probe arm adjustments for this data
   */
  def armAdjustment(ctx: ObsContext, offset: Offset): Option[ArmAdjustment] =
    for {
      c            <- Option(ctx)
      guideTargets <- ctx.getTargets.getPrimaryGuideProbeTargets(guideProbeInstance).asScalaOpt
      guideStar    <- guideTargets.getPrimary.asScalaOpt
      adj          <- armAdjustment(ctx, guideStar, offset)
    } yield adj

  def armAdjustmentAsJava(ctx: ObsContext, offset: Offset): GOption[ArmAdjustment] =
    armAdjustment(ctx, offset).asGeminiOpt
}

object ProbeArmGeometry {
  private lazy val maxArcsecs = 360 * 60 * 60d

  /**
   * Convert a value in arcseconds (i.e. in the range [0,maxArcsecs) to its canonical value, i.e.
   * the equivalent value in the range [-maxArcsecs/2,maxArcsecs/2).
   */
  implicit class ArcsecCanonicalValue(val v: Double) extends AnyVal {
    def toCanonicalArcsec: Double = {
      val v1 = math.IEEEremainder(v, maxArcsecs)
      val v2 = v1 - maxArcsecs
      if (math.abs(v1) <= math.abs(v2)) v1 else v2
    }
  }

  /**
   * Convert a point representing coordinates in arcsec to canonical value in both coordinates.
   */
  implicit class ArcsecCanonicalPoint(val p: Point2D) extends AnyVal {
    def toCanonicalArcsec: Point2D =
      new Point2D.Double(p.getX.toCanonicalArcsec, p.getY.toCanonicalArcsec)
  }
}
