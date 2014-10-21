// Copyright 1997-2011
// Association for Universities for Research in Astronomy, Inc.,
// Observatory Control System, Gemini Telescopes Project.
// See the file COPYRIGHT for complete details.
//
// $Id$

package edu.gemini.spModel.gemini.calunit.smartgcal;

import java.io.Serializable;
import java.util.List;

/**
 * A calibration provider allows to lookup the calibrations that are related to a given instrument configuration
 * represented by a calibration lookup key.
 */
public interface CalibrationProvider extends Serializable {

    public static enum GNIRSMode {
        IMAGING,
        SPECTROSCOPY
    }

    /**
     * Gets a sequence (list) of calibrations needed for a given instrument configuration (represented by a
     * lookup key).
     * @param key
     * @return
     */
    List<Calibration> getCalibrations(CalibrationKey key);

    /**
     * Gets the version of the calibration table of the given type for the given instrument.
     * @param type
     * @param instrument
     * @return
     */
    Version getVersion(Calibration.Type type, String instrument);

    /**
     * Gets version information for all calibration tables.
     * @return
     */
    List<VersionInfo> getVersionInfo();

}