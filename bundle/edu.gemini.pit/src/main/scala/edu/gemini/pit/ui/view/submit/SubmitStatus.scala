package edu.gemini.pit.ui.view.submit

import edu.gemini.model.p1.immutable._
import edu.gemini.pit.ui.robot.ProblemRobot._
import edu.gemini.model.p1.submit.{SubmitResult, SubmitDestination}
import edu.gemini.model.p1.submit.SubmitResult.{Offline, ServiceError, Failure}

object SubmitStatus {

  def forProposal(p:ProposalClass, ps:List[Problem]):SubmitStatus = p match {
    case _ if !ps.map(_.severity).filter(s => s == Severity.Error || s == Severity.Todo).isEmpty  => Incomplete
    case _ if p.key.isEmpty                                             => Ready
    case q:QueueProposalClass                                           => q.subs match {
      case Left(ss) if ss.forall(_.response.isDefined) => Success
      case Right(s) if s.response.isDefined            => Success
      case _                                           => Partial
    }
    case c:ClassicalProposalClass                                       => c.subs match {
      case Left(ss) if ss.forall(_.response.isDefined) => Success
      case Right(s) if s.response.isDefined            => Success
      case _                                           => Partial
    }
    case e:ExchangeProposalClass if e.subs.forall(_.response.isDefined) => Success
    case s:SpecialProposalClass if s.sub.response.isDefined             => Success
    case l:LargeProgramClass if l.sub.response.isDefined                => Success
    case f:FastTurnaroundProgramClass if f.sub.response.isDefined       => Success
    case _                                                              => Partial
  }

  def destinationName(destination: Option[SubmitDestination]) = destination.map(d => d.destinationName).getOrElse("Unknown")

  def semesterName(semester: Option[Semester]) = semester.map(s => s.display).getOrElse("Unknown")

  def nonCompliantBackend(destination: Option[SubmitDestination], semester: Option[Semester]) = s"The ${destinationName(destination)} backend server is configured to accept proposals from the ${semesterName(semester)} PIT"

  def offlineBackend(destination: Option[SubmitDestination]) = s"There is a connection problem with the ${destinationName(destination)} backend server. Please check your network connection and/or try again later."

  def genericError(destination: Option[SubmitDestination]) = s"The ${destinationName(destination)} backend server returned an unexpected result. Please try again later or submit a Helpdesk ticket at http://www.gemini.edu/sciops/helpdesk/."

  def lpSubmissionClosed(destination: Option[SubmitDestination]) = s"""The ${destinationName(destination)} proposal server is currently closed. Please see the Gemini web pages for proposal submission dates. Note that Large & Long programs are only accepted for "B" semesters."""

  def submissionClosed(destination: Option[SubmitDestination]) = s"The ${destinationName(destination)} proposal server is currently closed. Please see the Gemini web pages for proposal submission dates"

  private val ErrorRegex = """.* (\d\d\d\d.) .*""".r // Extract semester from the backend error message

  def msg(results: Seq[SubmitResult]):Seq[String] = results.collect {
    case ServiceError(destination, code, message) if code == 405 =>
      val ErrorRegex(semester) = message
      nonCompliantBackend(destination, Semester.parse(semester))
    case ServiceError(d @ Some(SubmitDestination.LargeProgram), code, message) if code == 401 =>
      lpSubmissionClosed(d)
    case ServiceError(destination, code, message) if code == 401 =>
      submissionClosed(destination)
    case ServiceError(destination, _, _)                         => genericError(destination)
    case Offline(destination)                                    => offlineBackend(destination)
    case f: Failure                                              => genericError(f.destination)
  }

}

sealed trait SubmitStatus {
  def title:String
  def description:String
}

case object Incomplete extends SubmitStatus {
  val title = "Incomplete"
  val description =
    "This proposal has problems or to-do tasks that must be corrected prior to submission (see the Problems view below)."
}

case object Ready extends SubmitStatus {
  val title = "Ready"
  val description =
    "This proposal is ready for submission. Please double-check your PDF attachment and the generated cover material; " +
      "once submitted, this proposal will be locked (but you will be able to open an editable copy)."
}

case object Partial extends SubmitStatus {
  val title = "Partially Submitted"
  val description =
    "Submission failed for at least one partner. Please wait a while and try again later. This proposal is locked (but " +
      "you can open an editable copy)."
}

case object Success extends SubmitStatus {
  val title = "Successfully Submitted"
  val description =
    "This proposal has been submitted and is locked (but you can open an editable copy)."
}

