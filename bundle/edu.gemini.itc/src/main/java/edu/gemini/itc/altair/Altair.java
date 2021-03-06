package edu.gemini.itc.altair;

import edu.gemini.itc.gems.GemsFluxAttenuationVisitor;
import edu.gemini.itc.shared.AOSystem;
import edu.gemini.itc.shared.FormatStringWriter;
import edu.gemini.itc.shared.SampledSpectrumVisitor;
import edu.gemini.spModel.gemini.altair.AltairParams;

/**
 * Altair AO class
 */
public class Altair implements AOSystem {
    /**
     * Related files will be in this subdir of lib
     */
    public static final String ALTAIR_LIB = "/altair";

    /**
     * Related files will start with this prefix
     */
    public static final String ALTAIR_PREFIX = "altair_";

    /**
     * Name of the altair background file
     */
    public static final String ALTAIR_BACKGROUND_FILENAME = "background";

    /**
     * Name of the Altair transmission file
     */
    public static final String ALTAIR_TRANSMISSION_FILENAME = "transmission";

    private static final double geometricFactor = 1.0;

    private final AltairParameters altair;
    private final double wavelength;
    private final double telescopeDiameter;
    private final double uncorrectedSeeing;
    private final double fwhmInst;

    private final AltairBackgroundVisitor altairBackground;
    private final AltairTransmissionVisitor altairTransmission;

    public Altair(double wavelength, double telescopeDiameter, double uncorrectedSeeing, AltairParameters altair, double fwhmInst) {
        this.altair = altair;
        this.wavelength = wavelength;
        this.telescopeDiameter = telescopeDiameter;
        this.uncorrectedSeeing = uncorrectedSeeing;
        this.fwhmInst = fwhmInst;
        this.altairBackground = new AltairBackgroundVisitor();
        this.altairTransmission = new AltairTransmissionVisitor();
    }

    //Methods
    public SampledSpectrumVisitor getBackgroundVisitor() {
        return altairBackground;
    }

    public SampledSpectrumVisitor getTransmissionVisitor() {
        return altairTransmission;
    }

    public SampledSpectrumVisitor getFluxAttenuationVisitor() {
        return new AltairFluxAttenuationVisitor(getFluxAttenuation());
    }

    public SampledSpectrumVisitor getHaloFluxAttenuationVisitor() {
        return new GemsFluxAttenuationVisitor(1 - getStrehl());
    }

    public double getr0() {
        return (0.1031 / uncorrectedSeeing) * Math.pow(wavelength / 500, 1.2);
    }

    // Calculates StrehlFit which is used to calc the strehl
    public double getStrehlFit() {
        double r0power, corr;
        double r0 = getr0();

        if (altair.getWFSMode().equals(AltairParams.GuideStarType.NGS)) {
            r0power = 0.96; //if NGS
            corr = 0.20; //if NGS
        } else {
            r0power = 0.79; //if LGS
            corr = 0.37; //if LGS
        }


        //double sigma = 0.04*Math.pow((telescopeDiameter/r0),1.66);  //old equation
        double sigma = corr * Math.pow((telescopeDiameter / r0), r0power);
        double strehl = Math.exp(-1 * sigma);

        return strehl;
    }

    // Calculates the strehl noise from the guideStar Magnitude
    //  the return values is used to calculate the strehl
    public double getStrehlNoiseMag() {
        double rgs0;

        if (altair.getWFSMode().equals(AltairParams.GuideStarType.NGS)) {
            rgs0 = 14.0; //if NGS
        } else {
            rgs0 = 17.0; //if LGS
        }

        return Math.exp(-1 * Math.pow(altair.getGuideStarMagnitude() / rgs0, 16) * Math.pow(1650 / wavelength, 2));
    }

    // Calculates the strehl noise from the guide star distance
    //  the return values is used to calculate the strehl
    public double getStrehlNoiseDist() {
        double dgs0, dgspow;

        if (fieldLensIsIn()) {
            dgs0 = 30d; //if FL in (FL always in for LGS)
            dgspow = 2d; //if FL in (FL always in for LGS)

        } else {
            dgs0 = 5d; //if FL out
            dgspow = 1d; //if FL out
        }
        //double strehlNoiseDistance = Math.exp(-1*Math.pow(guideStarDistance/12.5,2)*Math.pow(1650/wavelength,2));  //Old caclulation

        return Math.exp(-1 * Math.pow(altair.getGuideStarSeperation() / dgs0, dgspow) * Math.pow(1650 / wavelength, 2));
    }

    // Function that calculates the strehl from the fit, distance and magnitude
    public double getStrehl() {
        return getStrehlFit() * getStrehlNoiseDist() * getStrehlNoiseMag();
    }

    public double getFluxAttenuation() {
        return geometricFactor * getStrehl();
    }

    public double getAOCorrectedFWHM() {
        double fwhmAO = Math.sqrt(6.817E-10 * Math.pow(wavelength, 2) + 6.25E-4);
        return Math.sqrt(fwhmAO * fwhmAO + fwhmInst * fwhmInst); // REL-472
    }

    private boolean fieldLensIsIn() {
        return altair.getFieldLens().equals(AltairParams.FieldLens.IN);
    }


    public String printSummary() {
        final FormatStringWriter device = new FormatStringWriter();
        device.setPrecision(3);
        String s = "r0(" + wavelength + "nm) = " + device.toString(getr0()) + " m\n";
        s += "Strehl = " + device.toString(getStrehl()) + "\n";
        s += "FWHM of an AO-corrected core = " + device.toString(getAOCorrectedFWHM()) + " arcsec\n";
        return s;
    }

}
