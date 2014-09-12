package edu.gemini.phase2.skeleton.factory

import edu.gemini.spModel.gemini.obscomp.SPSiteQuality

import scala.collection.JavaConverters._
import edu.gemini.spModel.target.SPTarget
import edu.gemini.model.p1.immutable._
import edu.gemini.spModel.template.{TemplateGroup, SpBlueprint, TemplateFolder}
import edu.gemini.spModel.gemini.gnirs.blueprint.SpGnirsBlueprintSpectroscopy
import edu.gemini.spModel.gemini.nifs.blueprint.SpNifsBlueprintAo
import edu.gemini.shared.util.TimeValue
import edu.gemini.spModel.gemini.flamingos2.blueprint.SpFlamingos2BlueprintLongslit

object TemplateFolderFactory {

  case class ObsQuad(bName: String, tName: String, sName: String, time: TimeValue)

  object Folder {
    def empty = new Folder(new Namer, Map.empty, Map.empty, Map.empty, Nil)
  }

  implicit def RightProjection[A,B](v: Either[A,B]): Either.RightProjection[A,B] = v.right

  case class Folder(namer: Namer,
                    bMap: Map[String, SpBlueprint],
                    tMap: Map[String, SPTarget],
                    sMap: Map[String, SPSiteQuality],
                    oList: List[ObsQuad]) {

    def add(o: Observation, time: Long): Either[String, Folder] =
      for {
        blueprint   <- extractBlueprint(o).right
        target      <- extractTarget(o, time).right
        siteQuality <- extractSiteQuality(o).right
        timeValue   <- extractObsTime(o).right
      } yield Folder(namer,
                bMap + blueprint,
                tMap + target,
                sMap + siteQuality,
                ObsQuad(blueprint._1, target._1, siteQuality._1, timeValue) :: oList)

    private def extractBlueprint(o: Observation): Either[String, (String, SpBlueprint)] =
      for {
        b1 <- o.blueprint.toRight("Observation missing instrument resources").right
        b2 <- SpBlueprintFactory.create(b1).right
        bn = namer.nameOf(b1)
      } yield (bn -> bMap.getOrElse(bn, b2))

    private def extractTarget(o: Observation, time: Long): Either[String, (String, SPTarget)] =
      for {
        t1 <- o.target.toRight("Observation missing target").right
        t2 <- SpTargetFactory.create(t1, time).right
        tn = namer.nameOf(t1)
      } yield (tn -> tMap.getOrElse(tn, t2))

    private def extractSiteQuality(o: Observation): Either[String, (String, SPSiteQuality)] =
      for {
        s1 <- o.condition.toRight("Observation missing conditions").right
        s2 <- SpSiteQualityFactory.create(s1).right
        sn = namer.nameOf(s1)
      } yield (sn -> sMap.getOrElse(sn, s2))

    private def extractObsTime(o: Observation): Either[String, TimeValue] =
      for {
        time <- o.time.toRight("Observation missing time").right
      } yield new TimeValue(time.toHours.hours, TimeValue.Units.hours)

    def toTemplateFolder: TemplateFolder = {

      val groups = oList.groupBy(_.bName).toList map {
        case (blueprintId, args) =>
          val templateArgs = args map { arg => new TemplateGroup.Args(arg.tName, arg.sName, arg.time) }
          new TemplateFolder.Phase1Group(blueprintId, templateArgs.asJava)
      }

      // Some kinds of blueprints need to be parameterized by the H-magnitidude of their targets, which
      // kind of sucks. Sorry.

      val groups0 = groups.flatMap { pig =>
        bMap(pig.blueprintId) match {
          case _:SpGnirsBlueprintSpectroscopy => GnirsSpectroscopyPartitioner.partition(pig, tMap)
          case _:SpNifsBlueprintAo => NifsAoPartitioner.partition(pig, tMap)
          case _:SpFlamingos2BlueprintLongslit => F2LongslitPartitioner.partition(pig, tMap)
          case _ => List(pig)
        }
      }

      new TemplateFolder(
        bMap.asJava,
        tMap.asJava,
        sMap.asJava,
        groups0.asJava
      )
    }
  }



  // If there is an itac acceptance, then use its band assignment.  Otherwise
  // just figure we will use the "normal" band 1/2 observations.
  private def band(proposal: Proposal): Band =
    (for {
      itac   <- proposal.proposalClass.itac
      accept <- itac.decision.right.toOption
    } yield accept.band).getOrElse(1) match {
      case 3 => Band.BAND_3
      case _ => Band.BAND_1_2
    }

  def create(proposal: Proposal): Either[String, TemplateFolder] = {
    val empty: Either[String, Folder] = Right(Folder.empty)

    val b       = band(proposal)
    val time    = proposal.semester.midPoint
    val efolder = (empty/:proposal.observations.filter(obs => obs.band == b && obs.enabled)) { (e, obs) =>
      e.right flatMap { _.add(obs, time) }
    }

    efolder.right map { _.toTemplateFolder }
  }

}


trait Partitioner {
  def partition(pig:TemplateFolder.Phase1Group, tMap:Map[String, SPTarget]):List[TemplateFolder.Phase1Group] = {
    val argsLists = pig.argsList.asScala.toList.groupBy(args => bucket(tMap(args.getTargetId))).map(_._2.asJava).toList
    argsLists.map(args => new TemplateFolder.Phase1Group(pig.blueprintId, args))
  }
  def bucket(t:SPTarget):Int
}

object GnirsSpectroscopyPartitioner extends Partitioner {
  import edu.gemini.shared.skyobject.Magnitude.Band.H
  def bucket(t:SPTarget):Int = Option(t.getMagnitude(H).getOrNull).map(_.getBrightness).map {H =>
    if (H < 11.5) 1
    else if (H < 16) 2
    else if (H < 20) 3
    else 4
  }.getOrElse(5)
}

// TARGET BRIGHTNESS = TB
// Use H mag from target information if available
//     Bright target (H <= 9) = BT
//     Moderate target (9 < H <= 12) = MT
//     Faint target (12 < H <= 20) = FT
//     Blind acquisition target (H > 20) = BAT

object NifsAoPartitioner extends Partitioner {
  import edu.gemini.shared.skyobject.Magnitude.Band.H
  def bucket(t:SPTarget):Int = Option(t.getMagnitude(H).getOrNull).map(_.getBrightness).map {H =>
    if (H <= 9) 1
    else if (H <= 12) 2
    else if (H <= 20) 3
    else 4
  }.getOrElse(4) // treat as very faint for now
}

//IF TARGET H-MAGNITUDE < 7 INCLUDE {13} # Bright, no sky subtraction
//IF TARGET H-MAGNITUDE > 7 INCLUDE {14} # Faint, with sky subtraction
//ELSE INCLUDE {13,14}                   # Unknown mag so include both acq templates

object F2LongslitPartitioner extends Partitioner {
  import edu.gemini.shared.skyobject.Magnitude.Band.H
  def bucket(t: SPTarget): Int = (Option(t.getMagnitude(H).getOrNull).map(_.getBrightness) map { h =>
    if (h <= 12.0) 1 else 2
  }).getOrElse(3)
}



