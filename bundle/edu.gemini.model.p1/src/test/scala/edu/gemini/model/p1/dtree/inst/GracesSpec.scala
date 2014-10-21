package edu.gemini.model.p1.dtree.inst

import org.specs2.mutable.SpecificationWithJUnit
import edu.gemini.model.p1.immutable.GracesFiberMode

class GracesSpec extends SpecificationWithJUnit {
  "The Graces decision tree" should {
    "includes Graces observing modes" in {
      val graces = Graces()
      graces.title must beEqualTo("Fiber Mode")
      graces.choices must have size (2)
    }
    "build a Graces blueprint" in {
      val gpi = Graces()
      val blueprint = gpi.apply(GracesFiberMode.forName("ONE_FIBER"))
      blueprint must beRight
    }
  }

}