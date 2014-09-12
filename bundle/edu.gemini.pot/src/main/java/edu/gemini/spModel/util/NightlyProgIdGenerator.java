package edu.gemini.spModel.util;

import edu.gemini.shared.util.GeminiRuntimeException;
import edu.gemini.skycalc.ObservingNight;
import edu.gemini.spModel.core.SPBadIDException;
import edu.gemini.spModel.core.SPProgramID;
import edu.gemini.spModel.core.Site;

/**
 * Utility class for generating program ids based on the UTC date at the end
 * of the observing night.
 */
public class NightlyProgIdGenerator {
    /**
     * Name of system property that can indicate a factor used to shift the time when building a new daily plan
     * The factor is a time in seconds to shift the time up or down. Can be used for dst time changes that
     * are not supported by the JVM
     */
    public static final String LOCAL_TIME_SHIFT_FACTOR = "ocs.localTimeShiftFactor";

    /**
     * The prefix used for calibration programs.  This prefix will appear in
     * IDs generated by the {@link #getCalibrationID} method.
     */
    public static final String CAL_ID_PREFIX = "CAL";

    /**
     * The prefix used for engineering programs.  This prefix will appear in
     * IDs generated by the {@link #getEngineeringID} method.
     */
    public static final String ENG_ID_PREFIX = "ENG";

    /**
     * The prefix used for plans.  This prefix will appear in IDs generated
     * by the {@link #getPlanID} method.
     */
    public static final String PLAN_ID_PREFIX = "PLAN";

    private static Site _getSiteFromString(String siteStr) {
        if ("south".equals(siteStr)) {
            return Site.GS;
        } else {
            return Site.GN;
        }
    }

    /**
     * Generates a program id for science plans.  The id will be of the form
     * G[Ns]-PLAN${date}, where date is the UTC date when the night ends.
     * See {@link edu.gemini.skycalc.ObservingNight} for more information.
     *
     * @param site observatory site
     *
     * @return new plan id
     */
    public static SPProgramID getPlanID(Site site) {
        return getProgramID(PLAN_ID_PREFIX, site);
    }

    /**
     * Generates a program id for science plans.  The id will be of the form
     * G[Ns]-PLAN${date}, where date is the UTC date when the night ends.
     * See {@link edu.gemini.skycalc.ObservingNight} for more information.
     *
     * @param siteStr observatory site, which should be "north" or "south"
     *
     * @return new plan id
     */
    public static SPProgramID getPlanID(String siteStr) {
        return getPlanID(_getSiteFromString(siteStr));
    }

    /**
     * Generates a calibration program id.  The id will be of the form
     * G[Ns]-CAL${date}, where date is the UTC date when the night ends.
     * See {@link ObservingNight} for more information.
     *
     * @param site observatory site
     *
     * @return new calibration program id
     */
    public static SPProgramID getCalibrationID(Site site) {
        return getProgramID(CAL_ID_PREFIX, site);
    }

    /**
     * Generates a calibration program id.  The id will be of the form
     * G[Ns]-CAL${date}, where date is the UTC date when the night ends.
     * See {@link ObservingNight} for more information.
     *
     * @param siteStr observatory site, which should be "north" or "south"
     *
     * @return new calibration program id
     */
    public static SPProgramID getCalibrationID(String siteStr) {
        return getCalibrationID(_getSiteFromString(siteStr));
    }

    /**
     * Generates an engineering program id.  The id will be of the form
     * G[Ns]-ENG${date}, where date is the UTC date when the night ends.
     * See {@link ObservingNight} for more information.
     *
     * @param site observatory site
     *
     * @return new engineering program id
     */
    public static SPProgramID getEngineeringID(Site site) {
        return getProgramID(ENG_ID_PREFIX, site);
    }

    /**
     * Generates an engineering program id.  The id will be of the form
     * G[Ns]-ENG${date}, where date is the UTC date when the night ends.
     * See {@link ObservingNight} for more information.
     *
     * @param siteStr observatory site, which should be "north" or "south"
     *
     * @return new engineering program id
     */
    public static SPProgramID getEngineeringID(String siteStr) {
        return getEngineeringID(_getSiteFromString(siteStr));
    }

    /**
     * Generates a program id of the form G[NS]-${prefix}${date}, where the
     * date is the UTC date of the current night.
     *
     * @param prefix a string such as PLAN or CAL to be included in the id
     * @param site observatory site for which the id is sought
     *
     * @return the new program id
     */
    public static SPProgramID getProgramID(String prefix, Site site) {
        return getProgramID(prefix, site, System.currentTimeMillis());
    }

    /**
     * Generates a program id of the form G[NS]-${prefix}${date}, where the
     * date is the UTC date of the current night.
     *
     * @param prefix a string such as PLAN or CAL to be included in the id
     * @param siteStr code for observatory for which the id is sought; must
     * be one of "north" or "south"
     *
     * @return the new program id
     */
    public static SPProgramID getProgramID(String prefix, String siteStr) {
        return getProgramID(prefix, _getSiteFromString(siteStr));
    }

    /**
     * Generates a program id of the form G[NS]-${prefix}${date}.
     *
     * @param prefix a string such as "PLAN" or "CAL" to be included in the id
     * @param site observatory location
     * @param time time for which the program id should be calculated; date
     * will be the UTC date for the end of the night indicated
     *
     * @return the new program id
     */
    public static SPProgramID getProgramID(String prefix, Site site, long time) {
        if (Site.GS.equals(site)) {
            prefix = "GS-" + prefix;
        } else {
            prefix = "GN-" + prefix;
        }

        int shift = 0;
        if (System.getProperty(LOCAL_TIME_SHIFT_FACTOR) != null) {
            try {
                shift = 1000 * Integer.parseInt(System.getProperty(LOCAL_TIME_SHIFT_FACTOR));
            } catch (NumberFormatException e) {
                // Ignore, if the time shift isn't correctly formatted ignore it
            }
        }

        ObservingNight on = new ObservingNight(site, time + shift);

        // Get the UTC date string at this time.
        String dateStr = on.getNightString();
        try {
            return SPProgramID.toProgramID(prefix + dateStr);
        } catch (SPBadIDException ex) {
            throw new GeminiRuntimeException("unexpected bad id: " + prefix + dateStr);
        }
    }

    /**
     * Generates a program id of the form G[NS]-${prefix}${date}.
     *
     * @param prefix a string such as "PLAN" or "CAL" to be included in the id
     * @param siteStr should be "north" or "south"
     * @param time time for which the program id should be calculated; date
     * will be the UTC date for the end of the night indicated
     *
     * @return the new program id
     */
    public static SPProgramID getProgramID(String prefix, String siteStr, long time) {
        return getProgramID(prefix, _getSiteFromString(siteStr), time);
    }
}
