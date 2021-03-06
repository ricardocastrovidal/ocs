// Copyright 2001 Association for Universities for Research in Astronomy, Inc.,
// Observatory Control System, Gemini Telescopes Project.
// See the file COPYRIGHT for complete details.
//
// $Id: PlannedTimeSummaryService.java 46768 2012-07-16 18:58:53Z rnorris $
//
package edu.gemini.spModel.obs.plannedtime;

import edu.gemini.pot.sp.ISPNode;
import edu.gemini.pot.sp.ISPObservation;
import edu.gemini.pot.sp.ISPObservationContainer;
import edu.gemini.spModel.obs.ObsClassService;
import edu.gemini.spModel.obs.ObsPhase2Status;
import edu.gemini.spModel.obs.SPObsCache;
import edu.gemini.spModel.obs.SPObservation;
import edu.gemini.spModel.obsclass.ObsClass;


import java.util.Collection;


/**
 * A utility class used to calculate the total planned time for a set of
 * observations in a science program.
 *
 * @author Shane (but only slight modifications on Allan's original code)
 */
public final class PlannedTimeSummaryService {

    private PlannedTimeSummaryService() {
    }

    /**
     * Return the total planned observing time for the given program or group,
     * omitting inactive observations.
     *
     * @param node a program or group node
     */
    public static PlannedTimeSummary getTotalTime(ISPObservationContainer node) {
        return getTotalTime(node, false);
    }

    /**
     * Return the total planned observing time for the given program or group.
     *
     * @param node a program or group node
     * @param includeInactive flag to indicate whether inactive observations should be
     *                        included in total time calculation
     *
     * @return the total planned observing time
     */
    public static PlannedTimeSummary getTotalTime(ISPObservationContainer node, boolean includeInactive) {
        PlannedTimeSummary totalTime = PlannedTimeSummary.ZERO_PLANNED_TIME;

        Collection<ISPObservation> obsList;
        //noinspection unchecked
        obsList = node.getAllObservations();

        for (ISPObservation obs : obsList) {
            // If we are inactive and the includeInactive flag is false, we ignore this observation.
            boolean isInactive = ((SPObservation) obs.getDataObject()).getPhase2Status() == ObsPhase2Status.INACTIVE;
            if (includeInactive || !isInactive) {
                totalTime = totalTime.sum(getTotalTime(obs));
            }
        }
        return totalTime;
    }

    public static boolean shouldCountPlannedExecTime(ISPObservation obs) {
        ObsClass obsClass = ObsClassService.lookupObsClass(obs);
        if (obsClass == null) return false;

        return !((obsClass == ObsClass.ACQ) || (obsClass == ObsClass.ACQ_CAL) ||
                 (obsClass == ObsClass.DAY_CAL));
    }

    public static PlannedStepSummary getPlannedSteps(ISPObservation obs)  {

    	// First check the cache.
    	PlannedStepSummary steps = SPObsCache.getPlannedSteps(obs);
    	if (steps == null) {
       		getTotalTime(obs);
       		steps = SPObsCache.getPlannedSteps(obs);
       		assert steps != null;
    	}
   		return steps;

    }

    /**
     * Return the total observing time for the given observation.
     *
     * @param obs the observation to examine
     *
     * @return the total planned observing time for the observation
     */
    public static PlannedTimeSummary getTotalTime(ISPObservation obs) {

        // First check the cache.
        PlannedTimeSummary cachedTime = SPObsCache.getPlannedTime(obs);
        if (cachedTime != null) {
            return cachedTime;
        }

        // Figure out if we even count for exec time.
        // RCN: this will be a problem if we eventually decide to do queue planning
        // for the observe types that currently have a planned time of zero.
        if (!shouldCountPlannedExecTime(obs)) {// || !(includeInactive || isActive)) {
            PlannedTimeSummary res = PlannedTimeSummary.ZERO_PLANNED_TIME;
            PlannedStepSummary steps = PlannedStepSummary.ZERO_PLANNED_STEPS;
            SPObsCache.setPlannedTime(obs, res);
            SPObsCache.setPlannedSteps(obs, steps);
            return res;
        }

        PlannedTime pta = PlannedTimeCalculator.instance.calc(obs);

        // Cache the values.
        PlannedTimeSummary res = pta.toPlannedTimeSummary();
        SPObsCache.setPlannedTime(obs, res);
        SPObsCache.setPlannedSteps(obs, pta.toPlannedStepSummary());
        return res;
    }

    public static PlannedTimeSummary getTotalTime(ISPNode node) {
        if (node instanceof ISPObservationContainer) {
            return getTotalTime((ISPObservationContainer) node);
        } else if (node instanceof ISPObservation) {
            return getTotalTime((ISPObservation) node);
        } else {
            return PlannedTimeSummary.ZERO_PLANNED_TIME;
        }
    }
}

