package edu.gemini.phase2.template.factory.impl.gmos

import edu.gemini.pot.sp.{ISPObservation, ISPGroup}
import edu.gemini.spModel.gemini.gmos.blueprint.SpGmosNBlueprintMos
import edu.gemini.spModel.gemini.gmos.InstGmosCommon
import edu.gemini.spModel.gemini.gmos.GmosCommonType.Binning.ONE
import edu.gemini.phase2.template.factory.impl.TemplateDb
import edu.gemini.spModel.gemini.gmos.GmosNorthType.FPUnitNorth._

case class GmosNMos(blueprint:SpGmosNBlueprintMos) extends GmosNBase[SpGmosNBlueprintMos] {

  //  IF SPECTROSCOPY MODE == MOS
  //          INCLUDE FROM 'MOS BP' IN
  //              Target group:   {20}, {21} - {23}
  //                IF PRE-IMAGING REQ == YES
  //                  INCLUDE {18}, {17}
  //                IF PRE-IMAGING REQ == NO
  //                  INCLUDE {19}
  //              Baseline folder: {24}-{26}
  //          For spec observations: {20}, {21}, {23}, {25}, {26}
  //                  SET DISPERSER FROM PI
  //                  SET FILTER FROM PI
  //                  For {20}, {21}
  //                      SET MOS "Slit Width" from PI
  //                  For {25}, {26}
  //                      SET FPU (built-in longslit) using the width specified in PI
  //          For standard acquisition: {24}
  //              if FPU!=None in the OT inst. iterators, then SET FPU (built-in longslit) using the width specified in PI
  //          For acquisitions: {18}, {19}, and mask image {22}
  //              No actions needed

  val targetGroup = Seq(20, 21, 22, 23) ++ (if (blueprint.preImaging) Seq(18, 17) else Seq(19))
  val baselineFolder = 24 to 26
  val all = targetGroup ++ baselineFolder
  val spec = Seq(20, 21, 23, 25, 26).filter(all.contains)

  def noneOrPiFpu(libFpu: Any) = if (libFpu == FPU_NONE) FPU_NONE else blueprint.fpu

  def forSpecObservation(o:ISPObservation):Either[String, Unit] = for {
    _ <- o.setDisperser(blueprint.disperser).right
    _ <- o.setFilter(blueprint.filter).right
  } yield ()

  def forStandardAcq(o:ISPObservation):Either[String, Unit] = for {
    _ <- o.setFpu(blueprint.fpu).right
    _ <- o.ed.modifySeqAllKey(InstGmosCommon.FPU_PROP_NAME) { case libFpu => noneOrPiFpu(libFpu) }.right
  } yield ()

  val notes = Seq.empty

  def initialize(group:ISPGroup, db:TemplateDb):Either[String, Unit] = {
    val iniAo = withAoUpdate(db) _
    for {
      _ <- forObservations(group, spec, iniAo(forSpecObservation)).right
      _ <- forObservations(group, Seq(20, 21), _.setCustomSlitWidth(blueprint.fpu)).right
      _ <- forObservations(group, Seq(25, 26), iniAo(_.setFpu(blueprint.fpu))).right
      _ <- forObservations(group, Seq(24), iniAo(forStandardAcq)).right
      _ <- forObservations(group, Seq(17, 18, 19, 24).filter(all.contains), _.ifAo(_.setXyBin(ONE, ONE))).right
      _ <- forObservations(group, spec, _.ifAo(_.setYbin(ONE))).right
    } yield ()
  }

}
