package edu.gemini.ags.impl

import edu.gemini.ags.api.{AgsMagnitude, DefaultMagnitudeTable, AgsRegistrar, AgsStrategy}
import edu.gemini.pot.sp.SPComponentType
import edu.gemini.shared.skyobject.{Magnitude, SkyObject}
import edu.gemini.shared.skyobject.coords.{SkyCoordinates, HmsDegCoordinates}
import edu.gemini.skycalc.{Offset, DDMMSS, HHMMSS, Angle}
import edu.gemini.skycalc.Angle.ANGLE_0DEGREES
import edu.gemini.skycalc.Angle.Unit._
import edu.gemini.spModel.core.Site
import edu.gemini.spModel.gemini.altair.{InstAltair, AltairParams}
import edu.gemini.spModel.gemini.inst.InstRegistry
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality.Conditions
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality.Conditions._
import edu.gemini.spModel.guide.{GuideSpeed, GuideProbe}
import edu.gemini.spModel.guide.GuideSpeed._
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.rich.shared.immutable._
import edu.gemini.spModel.target.SPTarget
import edu.gemini.spModel.target.env.TargetEnvironment
import edu.gemini.spModel.telescope.IssPortProvider
import edu.gemini.spModel.telescope.IssPort._

import org.junit.Assert._

import java.awt.geom.{Point2D, PathIterator, AffineTransform, Area}

import scala.collection.JavaConverters._

/**
 * Support for running single-probe tests.
 */

object AgsTest {
  // Value to nudge things off of the border
  private val nudge = 1 //1e-3

  // Minimum distance that points must be from all points on the boundary defining points of the shape in order
  // to be considered for inclusion.
  private val minimumDistance = 1

  def apply(instType: SPComponentType, guideProbe: GuideProbe): AgsTest =
    apply(instType, guideProbe, None)

  def apply(instType: SPComponentType, guideProbe: GuideProbe, site: Site): AgsTest = {
    apply(instType, guideProbe, Some(site))
  }

  def apply(instType: SPComponentType, guideProbe: GuideProbe, site: Option[Site]): AgsTest = {
    val base       = new SPTarget(0.0, 0.0)
    val targetEnv  = TargetEnvironment.create(base).addActive(guideProbe)

    val inst = InstRegistry.instance.prototype(instType.narrowType).getValue
    inst match {
      case pp: IssPortProvider => pp.setIssPort(SIDE_LOOKING)
      case _ => // do nothing
    }
    inst.setPosAngleDegrees(0.0)

    val ctx = if (site.isDefined) ObsContext.create(targetEnv, inst, site.asGeminiOpt, BEST, java.util.Collections.emptySet(), null)
              else ObsContext.create(targetEnv, inst, BEST, java.util.Collections.emptySet(), null)
    AgsTest(
      ctx,
      guideProbe,
      Nil,
      Nil)
  }

  def skyObject(raDecStr: String, rMag: Double): SkyObject = {
    val (raStr, decStr) = raDecStr.span(_ != ' ')
    val ra  = HHMMSS.parse(raStr)
    val dec = DDMMSS.parse(decStr.trim)
    val sc  = new HmsDegCoordinates.Builder(ra, dec).build()
    new SkyObject.Builder(raDecStr, sc).magnitudes(new Magnitude(Magnitude.Band.R, rMag)).build()
  }

  def skyObjects(so: (String, Double)*): List[SkyObject] = {
    so.toList.map((skyObject _).tupled)
  }

  def usableSkyObjects(so: (String, Double, GuideSpeed)*): List[(SkyObject, GuideSpeed)] = {
    val sos = skyObjects(so.map { case (c, d, _) => (c, d) }: _*)
    sos.zip(so.map { case (_, _, gs) => gs })
  }
}

case class AgsTest(ctx: ObsContext, guideProbe: GuideProbe, usable: List[(SkyObject, GuideSpeed)], unusable: List[SkyObject],
                   calculateValidArea: (ObsContext, GuideProbe) => Area
                    = (ctx: ObsContext, probe: GuideProbe) => probe.getCorrectedPatrolField(ctx).getArea) {
  import AgsTest.{nudge, minimumDistance}
  type Point = Point2D.Double

  def unusable(so: (String, Double)*): AgsTest =
    copy(unusable = AgsTest.skyObjects(so: _*))

  def usable(so: (String, Double, GuideSpeed)*): AgsTest =
    copy(usable = AgsTest.usableSkyObjects(so: _*))

  def rotated(deg: Double): AgsTest =
    copy(ctx.withPositionAngle(new Angle(deg, DEGREES)))

  def withConditions(c: Conditions): AgsTest =
    copy(ctx.withConditions(c))

  def withAltair(mode: AltairParams.Mode) = {
    val aoComp = new InstAltair
    aoComp.setMode(mode)
    copy(ctx.withAOComponent(aoComp))
  }

  def withOffsets(offsets: (Double, Double)*) = {
    val o = offsets map {
      case (p, q) => new Offset(new Angle(p, ARCSECS), new Angle(q, ARCSECS))
    }
    copy(ctx.withSciencePositions(o.toSet.asJava))
  }

  def withValidArea(f: (ObsContext, GuideProbe) => Area): AgsTest =
    copy(calculateValidArea = f)

  // Calculate a list of candidates from the area, making sure that these points respect the minimum distance.
  private def areaCandidates(a: Area, maxOuterPoints: Int = 10): (List[Point],List[Point]) = {
    // Unfortunately, we need mutability here of an array due to Java.
    def pathSegments(t: AffineTransform = new AffineTransform()): List[(Int, List[Point])] = {
      def pathSegmentsAux(pi: PathIterator): List[(Int, List[Point])] = {
        if (pi.isDone) Nil
        else {
          val arr = new Array[Double](6)
          val curveType = pi.currentSegment(arr)
          pi.next()

          val (xcoords, ycoords) = arr.zipWithIndex.partition { case (_, idx) => idx % 2 == 0}
          val points = xcoords.map(_._1).zip(ycoords.map(_._1)).map{ case (x,y) => new Point(x,y)}.toList

          (curveType, points.take(curveType match {
            case PathIterator.SEG_CLOSE => 0
            case PathIterator.SEG_MOVETO | PathIterator.SEG_LINETO => 1
            case PathIterator.SEG_QUADTO => 2
            case PathIterator.SEG_CUBICTO => 3
          })) :: pathSegmentsAux(pi)
        }
      }
      pathSegmentsAux(a.getPathIterator(t))
    }

    // Given a list of points on an area, create outer points from these points by nudging them in four
    // different directions and then determining if they are in or out of the area. We allow limiting (default 1, up to 4)
    // of the number of points allowed to be generated for each point. A list with the candidates
    // outside of the area is returned.
    def createOuterPoints(points: List[Point], maxOuterPointsPerPoint: Int = 1): List[Point] =
      (for (p <- points) yield (for {
        xmult <- List(-1,1)
        ymult <- List(-1,1)
        newPoint = new Point(p.getX + xmult * nudge, p.getY + ymult * nudge)
        if points.filter(_.distance(newPoint) < minimumDistance).isEmpty && !a.contains(newPoint)
      } yield newPoint).take(maxOuterPointsPerPoint)).flatten

    // Try to find a single working point in the area through a (depth-limited) quaternary BFS search.
    def findInnerPoint(depth: Int = 4): Option[Point] = {
      def areaBFS(remain: Stream[((Double,Double),(Double,Double))], remainDepth: Int): Stream[Point] = {
        if (remainDepth <= 0) Stream.empty
        else {
          remain match {
            case s if s.isEmpty => Stream.empty
            case ((xMin, xMax), (yMin, yMax)) #:: tail =>
              if (xMax >= xMin && yMax >= yMin) {
                val xMid = xMin + (xMax - xMin) / 2
                val yMid = yMin + (yMax - yMin) / 2

                // The processing for this area.
                val curr = if (a.contains(xMid, yMid)) Stream(new Point(xMid, yMid))
                           else Stream.empty

                // The new areas to process in a BFS fashion.
                val newAreas = for {
                  newx <- Stream((xMin, xMid - nudge), (xMid + nudge, xMax))
                  newy <- Stream((yMin, yMid - nudge), (yMid + nudge, yMax))
                } yield (newx, newy)

                curr append areaBFS(tail append newAreas, remainDepth-1)
              }
              else areaBFS(tail, remainDepth-1)
          }
        }
      }

      val boundingBox = a.getBounds2D
      areaBFS(Stream(((boundingBox.getMinX, boundingBox.getMaxX), (boundingBox.getMinY, boundingBox.getMaxY))), depth).headOption
    }

    // Get the points that fall outside of the area, limited to maxOuterPoints.
    val outpoints = pathSegments().map(_._2).map(createOuterPoints(_)).flatten.take(maxOuterPoints)

    // Find an inner point to use, and return the candidates.
    (findInnerPoint().toList, outpoints)
  }


  private def boundingBoxCandidates(a: Area): (List[Point], List[Point]) = {
    val rect = a.getBounds2D

    val minX = rect.getMinX - nudge
    val minY = rect.getMinY - nudge
    val maxX = rect.getMaxX + nudge
    val maxY = rect.getMaxY + nudge
    val midX = minX + (maxX - minX)/2
    val midY = minY + (maxY - minY)/2

    val out = List(
      (minX, minY),
      (minX, midY),
      (minX, maxY),
      (midX, minY),
      (midX, maxY),
      (maxX, minY),
      (maxX, midY),
      (maxX, maxY)
    ).map{case (x,y) => new Point(x,y)}

    val in = List(new Point(midX, midY))

    (in, out)
  }

  private def genCandidates(a: Area, allUsable: Boolean = false, allUnusable: Boolean = false): AgsTest = {
    assertTrue(!(allUsable && allUnusable))

    //val (inTmp, outList) = boundingBoxCandidates(a)
    val (inTmp, outList) = areaCandidates(a)
    val inList = if (a.isEmpty) Nil else inTmp

    val out = ((if (!allUsable) outList else Nil) ++ (if (allUnusable)  inList else Nil)).toList
    val in  = ((if (allUsable)  outList else Nil) ++ (if (!allUnusable) inList else Nil)).toList

    val mt = new DefaultMagnitudeTable(ctx)
    val mc = mt.apply(ctx.getSite.getValue, guideProbe).get

    def mags = {
      val m    = GuideSpeed.values.toList.map { gs => gs -> mc.apply(ctx.getConditions, gs) }.toMap
      val fast = m(FAST)
      val band = fast.getBand

      def magList(base: Double)(adjs: (Double, Option[GuideSpeed])*): List[(Magnitude, Option[GuideSpeed])] =
        adjs.toList.map { case (adj, gs) => (new Magnitude(band, base + adj), gs) }

      val bright = fast.getSaturationLimit.asScalaOpt.map(_.getBrightness).toList.flatMap { brightness =>
        magList(brightness)((-0.01, None), (0.0, Some(FAST)), (0.01, Some(FAST)))
      }

      val faintFast = magList(fast.getFaintnessLimit.getBrightness)((-0.01, Some(FAST)), (0.0, Some(FAST)), (0.01, Some(MEDIUM)))
      val faintNorm = magList(m(MEDIUM).getFaintnessLimit.getBrightness)((-0.01, Some(MEDIUM)), (0.0, Some(MEDIUM)), (0.01, Some(SLOW)))
      val faintSlow = magList(m(SLOW).getFaintnessLimit.getBrightness)((-0.01, Some(SLOW)), (0.0, Some(SLOW)), (0.01, None))

      bright ++ faintFast ++ faintNorm ++ faintSlow
    }

    def toSkyCoordinates(lst: List[Point]): List[SkyCoordinates] = {
      val b = new HmsDegCoordinates.Builder(ANGLE_0DEGREES, ANGLE_0DEGREES)
      lst.map { p => b.ra(Angle.arcsecs(-p.getX).toPositive).dec(Angle.arcsecs(-p.getY)).build() }.toSet.toList
    }

    def candidates(lst: List[Point]): List[(SkyCoordinates, Magnitude, Option[GuideSpeed])] =
      for {
        sc <- toSkyCoordinates(lst)
        (mag, gs) <- mags
      } yield (sc, mag, gs)


    def name(base: String, i: Int): String =
      s"$base${ctx.getInstrument.getType.narrowType}($i)"

    val usableCandidates = candidates(in).zipWithIndex.collect { case ((sc, mag, Some(gs)), i) =>
      (new SkyObject.Builder(name("in", i), sc).magnitudes(mag).build(), gs)
    }

    val unusableCandidates = candidates(out).zipWithIndex.map { case ((sc, mag, _), i) =>
      new SkyObject.Builder(name("out", i), sc).magnitudes(mag).build()
    }

//    println("*****USABLE SKY CANDIDATES*****")
//    usableCandidates.foreach { case (c,_) =>
//      println(s"${c.getName}, ${c.getCoordinates}, ${c.getMagnitudes}")
//      //println(c.getName + " = " + c.getCoordinates + c.getMagnitudeBands)
//    }
//
//    println("*****UNUSABLE SKY CANDIDATES*****")
//    unusableCandidates.foreach { c =>
//      println(s"${c.getName}, ${c.getCoordinates}, ${c.getMagnitudes}")
//      //println(c.getName + " = " + c.getCoordinates)
//    }
//    Console.out.flush

    copy(usable = usableCandidates, unusable = unusableCandidates)
  }

  // A word about transforms.  The probe areas are specified in arcsecs but in
  // screen coordinates.  That means x increases to the right and y increases
  // toward the bottom.  When we provide offsets to the context, this is in
  // (p,q) so x increases to the left and y increases toward the top.  Rotation
  // is toward positive y in screen coordinates so again it is flipped with
  // respect to the position angle we set in the context.

  private def testXform(xform: AffineTransform = new AffineTransform(),
                        patrolField: Area = calculateValidArea(ctx, guideProbe),
                        allUsable: Boolean = false,
                        allUnusable: Boolean = false): Unit = {
    patrolField.transform(xform)
    genCandidates(patrolField, allUsable, allUnusable).test()
  }

  // Take a test case and produce three, one with each set of predefined conditions.
  private def allConditions(): List[AgsTest] =
    //List(BEST, WORST, NOMINAL).map(withConditions)
    List(BEST, WORST).map(withConditions)

  // Take a test case and produce 12, one with each 30 deg position angle
  private def allRotations(): List[AgsTest] =
    //(0 until 360 by 90).toList.map(_.toDouble).map(rotated)
    List(0.0, 270.0).map(rotated)

  // Take a test case and produce 36, one with each predefined condition and 30 deg position angle.
  private def allConditionsAndRotations(): List[AgsTest] =
    allConditions().map(_.allRotations()).flatten

  // Convenience function to calculate the proper rotation.
  private def rotation: AffineTransform =
    AffineTransform.getRotateInstance(-ctx.getPositionAngle.toRadians.getMagnitude)


  def testBase(): Unit =
    allConditions().foreach(_.testXform())

  def testBaseOneOffset(): Unit = {
    val xlat = AffineTransform.getTranslateInstance(-600.0, -600.0)
    allConditions().foreach(_.withOffsets((600.0, 600.0)).testXform(xlat))
  }

  def testBaseTwoDisjointOffsets(): Unit =
    allConditions().map(_.withOffsets((600.0, 600.0),(-600.0,-600.0))).foreach { tc =>
      tc.testXform(allUnusable = true)
      tc.testXform(patrolField = new Area(), allUnusable=true)
      tc.testXform(AffineTransform.getTranslateInstance(-600.0, -600.0), allUnusable = true)
      tc.testXform(AffineTransform.getTranslateInstance( 600.0,  600.0), allUnusable = true)

    }

  def testBaseTwoIntersectingOffsets(): Unit = {
    allConditions().map(_.withOffsets((400.0, 400.0), (300.0, 300.0))).map { tc =>
      // Construct the area consisting of the intersection of the patrol field with respect to both the offsets.
      val originalArea = calculateValidArea(ctx, guideProbe)
      val offsetArea1 = originalArea.createTransformedArea(AffineTransform.getTranslateInstance(-400.0, -400.0))
      val offsetArea2 = originalArea.createTransformedArea(AffineTransform.getTranslateInstance(-300.0, -300.0))

      // Intersection is a mutable operation, so finalArea will contain the intersection after this is done.
      val finalArea = new Area(offsetArea1)
      finalArea.intersect(offsetArea2)

      // At this point, the patrol field has already been translated, so no need to use a translation in testXform.
      tc.testXform(patrolField = finalArea)
    }
  }

  def testBaseRotated(): Unit =
    allConditionsAndRotations().foreach{ tc => tc.testXform(tc.rotation) }

  def testBaseRotatedOneOffset(): Unit = {
    val xlat = AffineTransform.getTranslateInstance(-600.0, -600.0)
    allConditionsAndRotations().foreach { tc =>
      val xlatRot = tc.rotation
      xlatRot.concatenate(xlat)
      tc.withOffsets((600.0, 600.0)).testXform(xlatRot)
    }
  }

  def testBaseRotatedTwoIntersectingOffsets(): Unit = {
    allConditionsAndRotations().map(_.withOffsets((400.0, 400.0), (300.0, 300.0))).map { tc =>
      // Construct the area consisting of the intersection of the patrol field with respect to both the offsets.
      val originalArea = calculateValidArea(ctx, guideProbe)

      val xlatRot1 = tc.rotation
      xlatRot1.concatenate(AffineTransform.getTranslateInstance(-400.0, -400.0))
      val offsetArea1  = originalArea.createTransformedArea(xlatRot1)

      val xlatRot2 = tc.rotation
      xlatRot2.concatenate(AffineTransform.getTranslateInstance(-300.0, -300.0))
      val offsetArea2  = originalArea.createTransformedArea(xlatRot2)

      // Intersection is a mutable operation, so finalArea will contain the intersection after this is done.
      val finalArea = new Area(offsetArea1)
      finalArea.intersect(offsetArea2)

      // At this point, the patrol field has already been transformed, so no need to use a transform in testXform.
      tc.testXform(patrolField = finalArea)
    }
  }


  // gets the selected single probe strategy, or blows up
  def strategy: SingleProbeStrategy =
    AgsRegistrar.selectedStrategy(ctx).get.asInstanceOf[SingleProbeStrategy]

  def test(): Unit = {
    val mt   = new DefaultMagnitudeTable(ctx)
    val mc   = mt.apply(ctx.getSite.getValue, guideProbe).get
    val band = mc.apply(ctx.getConditions, GuideSpeed.FAST).getBand
    val maxMag = new Magnitude(band, Double.MaxValue)

    def go(winners: List[(SkyObject, GuideSpeed)]): Unit = {
      val best = winners match {
        case Nil => None
        case lst => Some(lst.minBy(_._1.getMagnitude(band).getOrElse(maxMag)))
      }

      val all = winners.map(_._1) ++ unusable
      val res = strategy.select(ctx, new DefaultMagnitudeTable(ctx), all)

      def equalPosAngles(e: Angle, a: Angle): Unit =
        assertEquals("Position angles do not match", e.toDegrees.getMagnitude, a.toDegrees.getMagnitude, 0.000001)

      def expectNothing(): Unit =
        res match {
          case None                                       => // ok
          case Some(AgsStrategy.Selection(posAngle, Nil)) =>
            equalPosAngles(ctx.getPositionAngle, posAngle)
          case Some(AgsStrategy.Selection(_,        asn)) =>
              fail("Expected nothing but got: " + asn.map { a =>
                "(" + a.guideStar.toString + ", " + a.guideProbe + ")"
              }.mkString("[", ", ", "]"))
        }

      def expectSingleAssignment(expStar: SkyObject, expSpeed: GuideSpeed): Unit = {
        res match {
          case None      =>
            fail("Expected: (" + expStar + ", " + expSpeed + "), but nothing selected")
          case Some(AgsStrategy.Selection(posAngle, asn)) =>
            equalPosAngles(ctx.getPositionAngle, posAngle)
            asn match {
              case List(AgsStrategy.Assignment(actProbe, actStar)) =>
                assertEquals(guideProbe, actProbe)

                // Display the expected and selected star.
                //println(s"expStar=$expStar\n")
                //println(s"actStar=$actStar\n\n")
                //Console.out.flush

                assertEquals(expStar, actStar)
                val actSpeed = AgsMagnitude.fastestGuideSpeed(mc, actStar.getMagnitude(band).getValue, ctx.getConditions)
                assertTrue("Expected: " + expSpeed + ", actual: " + actSpeed, actSpeed.exists(_ == expSpeed))
              case Nil => fail("Expected: (" + expStar + ", " + expSpeed + "), but nothing selected")
              case _   => fail("Multiple guide probe assignments: " + asn)
            }
        }
      }

      best.fold(expectNothing()) { (expectSingleAssignment _).tupled }

      val remaining = best.map { case (so, gs) => winners.diff(List((so, gs)))}.toList.flatten
      if (best.isDefined) go(remaining)
    }

    go(usable)
  }
}