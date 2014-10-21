//
// $
//

package edu.gemini.spModel.gemini.gsaoi;

import edu.gemini.pot.sp.ISPObservation;
import edu.gemini.pot.sp.SPComponentType;
import edu.gemini.shared.skyobject.Magnitude.Band;
import edu.gemini.shared.util.immutable.*;
import edu.gemini.skycalc.Angle;
import edu.gemini.spModel.config.injector.ConfigInjector;
import edu.gemini.spModel.config.injector.obswavelength.ObsWavelengthCalc1;
import edu.gemini.spModel.config2.Config;
import edu.gemini.spModel.config2.ItemKey;
import edu.gemini.spModel.core.Site;
import edu.gemini.spModel.data.ISPDataObject;
import edu.gemini.spModel.data.config.DefaultParameter;
import edu.gemini.spModel.data.config.DefaultSysConfig;
import edu.gemini.spModel.data.config.ISysConfig;
import edu.gemini.spModel.data.config.StringParameter;
import edu.gemini.spModel.data.property.PropertyProvider;
import edu.gemini.spModel.data.property.PropertySupport;
import edu.gemini.spModel.gemini.gems.Canopus;
import edu.gemini.spModel.guide.GuideOption;
import edu.gemini.spModel.guide.GuideProbe;
import edu.gemini.spModel.guide.GuideProbeProvider;
import edu.gemini.spModel.guide.GuideProbeUtil;
import edu.gemini.spModel.obs.plannedtime.CommonStepCalculator;
import edu.gemini.spModel.obs.plannedtime.ExposureCalculator;
import edu.gemini.spModel.obs.plannedtime.OffsetOverheadCalculator;
import edu.gemini.spModel.obs.plannedtime.PlannedTime;
import edu.gemini.spModel.obs.plannedtime.PlannedTime.CategorizedTime;
import edu.gemini.spModel.obs.plannedtime.PlannedTime.CategorizedTimeGroup;
import edu.gemini.spModel.obs.plannedtime.PlannedTime.Category;
import edu.gemini.spModel.obs.plannedtime.PlannedTime.StepCalculator;
import edu.gemini.spModel.obscomp.InstConfigInfo;
import edu.gemini.spModel.obscomp.SPInstObsComp;
import edu.gemini.spModel.pio.ParamSet;
import edu.gemini.spModel.pio.Pio;
import edu.gemini.spModel.pio.PioFactory;
import edu.gemini.spModel.seqcomp.SeqConfigNames;
import edu.gemini.spModel.target.offset.OffsetPosBase;
import edu.gemini.spModel.telescope.IssPort;
import edu.gemini.spModel.telescope.IssPortProvider;
import edu.gemini.spModel.type.DisplayableSpType;
import edu.gemini.spModel.type.LoggableSpType;
import edu.gemini.spModel.type.SequenceableSpType;
import edu.gemini.spModel.type.SpTypeUtil;

import java.beans.PropertyDescriptor;
import java.util.*;
import java.util.logging.Logger;

import static edu.gemini.spModel.seqcomp.SeqConfigNames.INSTRUMENT_KEY;

/**
 * This class defines the GS AOI instrument.
 */
public final class Gsaoi extends SPInstObsComp implements PropertyProvider, GuideProbeProvider, IssPortProvider, StepCalculator {
//    From REL-439:
//    ----
//    OT changes:
//    The text in the GSAOI component describing each read mode, the exposure time warning/error limits,
//    and the readout overheads in the planned time calculations must be updated with the information below.
//    The default exposure time for the GSAOI component must be 60.0 seconds.
//
//    Bright Objects:
//    Low Noise Reads: 2 (1-1 Fowler Sample)
//    Read Noise : 28e-
//    Exposure Time : > 5.3 sec (recommended) 5.3 sec (minimum)
//    Readout overhead: 10 sec
//
//    Faint Objects / Broad Band Imaging
//    Low Noise Reads: 8 (4-4 Fowler Sample)
//    Read Noise : 13e-
//    Exposure Time : > 21.5 sec (recommended) 21.5 sec (min)
//    Readout overhead: 26 sec
//
//    Very Faint Objects / Narrow-band Imaging
//    Low Noise Reads: 16 (8-8 Fowler Sample)
//    Read Noise : 10e-
//    Exposure Time : > 42.5 sec (recommended) 42.5 sec (min)
//    Readout overhead: 48 sec

    private static Logger LOG = Logger.getLogger(Gsaoi.class.getName());

    public static enum ReadMode implements DisplayableSpType, SequenceableSpType, LoggableSpType {
        // Updated for REL-439
        BRIGHT("Bright Objects", "Bright", 2, 28, 5.3, 10),
        FAINT("Faint Objects / Broad-band Imaging", "Faint", 8, 13, 21.5, 26),
        VERY_FAINT("Very Faint Objects / Narrow-band Imaging", "V. Faint", 16, 10, 42.5, 48),;

        public static final ReadMode DEFAULT = ReadMode.BRIGHT;
        public static final ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "readMode");

        private final String displayValue;
        private final String logValue;
        private final int ndr;
        private final int readNoise;
        private final double minExposureTime; // seconds
        private final int overhead; // seconds

        private ReadMode(String displayValue, String logValue, int ndr, int readNoise, double minExposureTime,
                         int overhead) {
            this.displayValue = displayValue;
            this.logValue = logValue;
            this.ndr = ndr;
            this.readNoise = readNoise;
            this.minExposureTime = minExposureTime;
            this.overhead = overhead;
        }

        public String displayValue() {
            return displayValue;
        }

        public String logValue() {
            return logValue;
        }

        public String sequenceValue() {
            return name();
        }

        public int ndr() {
            return ndr;
        }

        public int readNoise() {
            return readNoise;
        }

        public double minExposureTimeSecs() {
            return minExposureTime;
        }

        public int overhead() {
            return overhead;
        }

        public String toString() {
            return displayValue;
        }

        /**
         * Returns the read mode matching the given name by searching through
         * the known types.  If not found, nvalue is returned.
         */
        public static ReadMode valueOf(String name, ReadMode nvalue) {
            return SpTypeUtil.oldValueOf(ReadMode.class, name, nvalue);
        }

    }

    // REL-445: Updated using the new 50/50 times below
    public static enum Filter implements DisplayableSpType, SequenceableSpType, LoggableSpType {
        Z("Z (1.015 um)", "Z",
                1.015, ReadMode.FAINT, 26.0, 4619, Band.J),
        HEI("HeI (1.083 um)", "HeI",
                1.083, ReadMode.VERY_FAINT, 72.6, 21792, Band.J),
        PA_GAMMA("Pa(gamma) (1.094 um)", "Pagma",
                1.094, ReadMode.VERY_FAINT, 122.0, 36585, Band.J),
        J_CONTINUUM("J-continuum (1.207 um)", "Jcont",
                1.207, ReadMode.VERY_FAINT, 32.6, 9793, Band.J),
        J("J (1.250 um)", "J",
                1.250, ReadMode.FAINT, 5.7, 1004, Band.J),
        H("H (1.635 um)", "H",
                1.635, ReadMode.BRIGHT, 12.0, 460, Band.H),
        PA_BETA("Pa(beta) (1.282 um)", "Pabeta",
                1.282, ReadMode.FAINT, 21.8, 3879, Band.J),
        H_CONTINUUM("H-continuum (1.570 um)", "Hcont",
                1.570, ReadMode.FAINT, 31.2, 5545, Band.H),
        CH4_SHORT("CH4(short) (1.580 um)", "CH4short",
                1.580, ReadMode.FAINT, 6.6, 1174, Band.H),
        FE_II("[Fe II] (1.644 um)", "FeII1644",
                1.644, ReadMode.FAINT, 24.9, 4416, Band.H),
        CH4_LONG("CH4(long) (1.690 um)", "CH4long",
                1.690, ReadMode.FAINT, 6.8, 1202, Band.H),
        H20_ICE("H20 ice (2.000 um)", "H20ice",
                2.000, ReadMode.FAINT, 19.1, 3395, Band.K),
        HEI_2P2S("HeI (2p2s) (2.058 um)", "HeI2p2s",
                2.058, ReadMode.FAINT, 28.3, 5032, Band.K),
        K_CONTINUUM1("Ks-continuum (2.093 um)", "Kcontshrt",
                2.093, ReadMode.FAINT, 7.8, 6069, Band.K),
        BR_GAMMA("Br(gamma) (2.166 um)", "Brgma",
                2.166, ReadMode.FAINT, 31.0, 5496, Band.K),
        K_CONTINUUM2("Kl-continuum (2.270 um)", "Kcontlong",
                2.270, ReadMode.FAINT, 33.3, 5911, Band.K),
        K_PRIME("K(prime) (2.120 um)", "Kprime",
                2.120, ReadMode.BRIGHT, 14.8, 566, Band.K),
        H2_1_0_S_1("H2 1-0 S(1) (2.122 um)", "H2(1-0)",
                2.122, ReadMode.FAINT, 27.5, 5400, Band.K),
        K_SHORT("K(short) (2.150 um)", "Kshort",
                2.150, ReadMode.BRIGHT, 14.4, 551, Band.K),
        K("K (2.200 um)", "K",
                2.200, ReadMode.BRIGHT, 12.3, 470, Band.K),
        H2_2_1_S_1("H2 2-1 S(1) (2.248 um)", "H2(2-1)",
                2.248, ReadMode.FAINT, 32.6, 5784, Band.K),
        CO("CO (2.360 um)", "CO2360",
                2.360, ReadMode.FAINT, 7.7, 1370, Band.K),
        DIFFUSER1("Diffuser1", "Diffuser1",
                0.0, ReadMode.BRIGHT, 0.0, 0),
        DIFFUSER2("Diffuser2", "Diffuser2",
                0.0, ReadMode.BRIGHT, 0.0, 0),
        BLOCKED("Blocked", "Blocked",
                0.0, ReadMode.BRIGHT, 0.0, 0),;

        public static Filter DEFAULT = Z;
        public static final ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "filter");

        private final String displayValue;
        private final String logValue;
        private final double wavelength;
        private final ReadMode readMode;
        private final double expTime5050;
        private final double expTimeHalfWell;
        private final Option<Band> catalogBand;

        private Filter(String displayValue, String logValue, double wavelength, ReadMode readMode, double expTime5050,
                       double expTimeHalfWell, Band catalogBand) {
            this.displayValue = displayValue;
            this.logValue = logValue;
            this.wavelength = wavelength;
            this.readMode = readMode;
            this.expTime5050 = expTime5050;
            this.expTimeHalfWell = expTimeHalfWell;
            this.catalogBand = new Some<Band>(catalogBand);
        }

        private Filter(String displayValue, String logValue, double wavelength, ReadMode readMode, double expTime5050,
                       double expTimeHalfWell) {
            this.displayValue = displayValue;
            this.logValue = logValue;
            this.wavelength = wavelength;
            this.readMode = readMode;
            this.expTime5050 = expTime5050;
            this.expTimeHalfWell = expTimeHalfWell;
            this.catalogBand = None.instance();
        }

        public String displayValue() {
            return displayValue;
        }

        public String logValue() {
            return logValue;
        }

        public double wavelength() {
            return wavelength;
        }

        public String formattedWavelength() {
            return String.format("%.3f", wavelength);
        }

        public ReadMode readMode() {
            return readMode;
        }

        public double exposureTime5050Secs() {
            return expTime5050;
        }

        public double exposureTimeHalfWellSecs() {
            return expTimeHalfWell;
        }

        public String sequenceValue() {
            return name();
        }

        public String toString() {
            return displayValue;
        }

        /**
         * Returns the filter matching the given name by searching through the
         * known types.  If not found, nvalue is returned.
         */
        public static Filter valueOf(String name, Filter nvalue) {
            return SpTypeUtil.oldValueOf(Filter.class, name, nvalue);
        }

        /**
         * Returns the filter matching the given magnitude band by searching through the
         * known types.  If not found, nvalue is returned.
         */
        public static Filter getFilter(Band band, Filter nvalue) {
            for(Filter filter : values()) {
                if (!filter.catalogBand.isEmpty() && filter.catalogBand.getValue() == band) {
                    return filter;
                }
            }
            return nvalue;
        }

        public Option<Band> getCatalogBand() {
            return catalogBand;
        }
    }

    public static enum UtilityWheel implements DisplayableSpType, SequenceableSpType, LoggableSpType {
        EXTRAFOCAL_LENS_1("Extra-focal lens 1", "xf 1"),
        EXTRAFOCAL_LENS_2("Extra-focal lens 2", "xf 2"),
        PUPIL_IMAGER("Pupil Imager", "pupil"),
        CLEAR("Clear", "clear"),;

        public static UtilityWheel DEFAULT = CLEAR;
        public static ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "utilityWheel");

        private final String displayValue;
        private final String logValue;

        private UtilityWheel(String displayValue, String logValue) {
            this.displayValue = displayValue;
            this.logValue = logValue;
        }

        public String displayValue() {
            return displayValue;
        }

        public String logValue() {
            return logValue;
        }

        public String sequenceValue() {
            return name();
        }

        public String toString() {
            return displayValue;
        }

        public static UtilityWheel valueOf(String name, UtilityWheel nvalue) {
            return SpTypeUtil.oldValueOf(UtilityWheel.class, name, nvalue);
        }

//        public static Option<UtilityWheel> valueOf(String name, Option<UtilityWheel> nvalue) {
//            UtilityWheel def = nvalue.isEmpty() ? null : nvalue.getValue();
//            UtilityWheel val = SpTypeUtil.oldValueOf(UtilityWheel.class, name, def);
//            None<UtilityWheel> none = None.instance();
//            return val == null ? none : new Some<UtilityWheel>(val);
//        }
    }

    public static enum Roi implements DisplayableSpType, SequenceableSpType, LoggableSpType {
        FULL_ARRAY("Full Array", "Full Array"),
        ARRAY_64("Array 64", "Array64x64"),
        ARRAY_128("Array 128", "Array128x128"),
        ARRAY_256("Array 256", "Array256x256"),
        ARRAY_512("Array 512", "Array512x512"),
        ARRAY_1K("Array 1K", "Array1kx1k"),
        CENTRAL_64("Central 64", "Det64x64"),
        CENTRAL_128("Central 128", "Det128x128"),
        CENTRAL_256("Central 256", "Det256x256"),
        CENTRAL_512("Central 512", "Det512x512"),
        CENTRAL_1K("Central 1K", "Det1kx1k"),
        CENTRAL_2K("Central 2K", "Det2kx2k");

        public static Roi DEFAULT = FULL_ARRAY;

        private final String displayValue;
        private final String logValue;

        private Roi(String displayValue, String logValue) {
            this.displayValue = displayValue;
            this.logValue = logValue;
        }

        public String displayValue() {
            return displayValue;
        }

        public String logValue() {
            return logValue;
        }

        public String sequenceValue() {
            return logValue;
        }

        public String toString() {
            return displayValue;
        }

        public static Roi valueOf(String name, Roi nvalue) {
            return SpTypeUtil.oldValueOf(Roi.class, name, nvalue);
        }
    }

    public static enum OdgwSize implements DisplayableSpType, SequenceableSpType, LoggableSpType {
        SIZE_4(4),
        SIZE_6(6),
        SIZE_8(8),
        SIZE_16(16),
        SIZE_32(32),
        SIZE_64(64);

        public static OdgwSize DEFAULT = SIZE_64;

        private final int size;

        private OdgwSize(int size) {
            this.size = size;
        }

        public int getSize() {
            return size;
        }

        public String displayValue() {
            return String.valueOf(size);
        }

        public String sequenceValue() {
            return displayValue();
        }

        public String logValue() {
            return displayValue();
        }

        public String toString() {
            return displayValue();
        }

        public static OdgwSize valueOf(String name, OdgwSize nvalue) {
            return SpTypeUtil.oldValueOf(OdgwSize.class, name, nvalue);
        }

//        public static Option<OdgwSize> valueOf(String name, Option<OdgwSize> nvalue) {
//            OdgwSize def = nvalue.isEmpty() ? null : nvalue.getValue();
//            OdgwSize val = SpTypeUtil.oldValueOf(OdgwSize.class, name, def);
//            None<OdgwSize> none = None.instance();
//            return val == null ? none : new Some<OdgwSize>(val);
//        }
    }


    private static final String VERSION = "2011A-1";

    public static final SPComponentType SP_TYPE =
            SPComponentType.INSTRUMENT_GSAOI;

    public static final String INSTRUMENT_NAME_PROP = "GSAOI";

    // REL-1103
    public static final double GUIDED_OFFSET_OVERHEAD = 30.0; // sec
    public static final CategorizedTime GUIDED_OFFSET_OVERHEAD_CATEGORIZED_TIME =
            CategorizedTime.fromSeconds(Category.CONFIG_CHANGE, GUIDED_OFFSET_OVERHEAD, OffsetOverheadCalculator.DETAIL);

    public static final PropertyDescriptor FILTER_PROP;
    public static final PropertyDescriptor READ_MODE_PROP;
    public static final PropertyDescriptor PORT_PROP;
    public static final PropertyDescriptor EXPOSURE_TIME_PROP;
    public static final PropertyDescriptor COADDS_PROP;
    public static final PropertyDescriptor POS_ANGLE_PROP;
    public static final PropertyDescriptor UTILITY_WHEEL_PROP;
    public static final PropertyDescriptor ROI_PROP;
    public static final PropertyDescriptor ODGW_SIZE_PROP;

    private static final Map<String, PropertyDescriptor> PRIVATE_PROP_MAP = new TreeMap<String, PropertyDescriptor>();
    public static final Map<String, PropertyDescriptor> PROPERTY_MAP = Collections.unmodifiableMap(PRIVATE_PROP_MAP);

    private static PropertyDescriptor initProp(String propName, boolean query, boolean iter) {
        PropertyDescriptor pd;
        pd = PropertySupport.init(propName, Gsaoi.class, query, iter);
        PRIVATE_PROP_MAP.put(pd.getName(), pd);
        return pd;
    }

    static {
        boolean query_yes = true;
        boolean iter_yes = true;
        boolean query_no = false;
        boolean iter_no = false;

        FILTER_PROP = initProp(Filter.KEY.getName(), query_yes, iter_yes);
        READ_MODE_PROP = initProp(ReadMode.KEY.getName(), query_yes, iter_yes);
        PORT_PROP = initProp("issPort", query_no, iter_no);
        EXPOSURE_TIME_PROP = initProp("exposureTime", query_no, iter_yes);
        COADDS_PROP = initProp("coadds", query_no, iter_yes);
        POS_ANGLE_PROP = initProp("posAngle", query_no, iter_no);

        UTILITY_WHEEL_PROP = initProp(UtilityWheel.KEY.getName(), query_no, iter_yes);
        UTILITY_WHEEL_PROP.setExpert(true);
        PropertySupport.setWrappedType(UTILITY_WHEEL_PROP, UtilityWheel.class);

        ODGW_SIZE_PROP = initProp("odgwSize", query_no, iter_yes);
        ODGW_SIZE_PROP.setExpert(true);
        ODGW_SIZE_PROP.setDisplayName("ODGW Size");
        PropertySupport.setWrappedType(ODGW_SIZE_PROP, OdgwSize.class);

        ROI_PROP = initProp("roi", query_no, iter_yes);
        ROI_PROP.setExpert(true);
        ROI_PROP.setDisplayName("Region of Interest");
        PropertySupport.setWrappedType(ROI_PROP, Roi.class);
    }

    private Filter filter = Filter.DEFAULT;
    private ReadMode readMode;
    private IssPort port = IssPort.UP_LOOKING;

    private UtilityWheel utilityWheel = UtilityWheel.DEFAULT;
    private OdgwSize odgwSize = OdgwSize.DEFAULT;
    private Roi roi = Roi.DEFAULT;

    public Gsaoi() {
        super(SP_TYPE);
        readMode = filter.readMode();
//        setExposureTime(getRecommendedExposureTimeSecs(filter, readMode));
        setExposureTime(60); // REL-445
    }

    public Map<String, PropertyDescriptor> getProperties() {
        return PROPERTY_MAP;
    }

    @Override
    public Set<Site> getSite() {
        return Site.SET_GS;
    }

    public String getPhaseIResourceName() {
        return "gemGSAOI";
    }

    public Filter getFilter() {
        return filter;
    }

    public void setFilter(Filter newValue) {
        Filter oldValue = getFilter();
        if (oldValue != newValue) {
            filter = newValue;
            firePropertyChange(FILTER_PROP.getName(), oldValue, newValue);
        }
    }

    public UtilityWheel getUtilityWheel() {
        return utilityWheel;
    }

    public void setUtilityWheel(UtilityWheel newValue) {
        UtilityWheel oldValue = getUtilityWheel();
        if (!oldValue.equals(newValue)) {
            utilityWheel = newValue;
            firePropertyChange(UTILITY_WHEEL_PROP.getName(), oldValue, newValue);
        }
    }

    public Roi getRoi() {
        return roi;
    }

    public void setRoi(Roi newValue) {
        Roi oldValue = getRoi();
        if (!oldValue.equals(newValue)) {
            roi = newValue;
            firePropertyChange(ROI_PROP.getName(), oldValue, newValue);
        }
    }

    public OdgwSize getOdgwSize() {
        return odgwSize;
    }

    public void setOdgwSize(OdgwSize newValue) {
        OdgwSize oldValue = getOdgwSize();
        if (!oldValue.equals(newValue)) {
            odgwSize = newValue;
            firePropertyChange(ODGW_SIZE_PROP.getName(), oldValue, newValue);
        }
    }


    public ReadMode getReadMode() {
        return readMode;
    }

    public void setReadMode(ReadMode newValue) {
        ReadMode oldValue = getReadMode();
        if (oldValue != newValue) {
            readMode = newValue;
            firePropertyChange(READ_MODE_PROP.getName(), oldValue, newValue);
        }
    }

    public IssPort getIssPort() {
        return port;
    }

    public void setIssPort(IssPort newValue) {
        IssPort oldValue = getIssPort();
        if (oldValue != newValue) {
            port = newValue;
            firePropertyChange(PORT_PROP.getName(), oldValue, newValue);
        }
    }

    public double getRecommendedExposureTimeSecs() {
        return getRecommendedExposureTimeSecs(getFilter(), getReadMode());
    }

    public static double getRecommendedExposureTimeSecs(Filter filter, ReadMode readMode) {
        double min = getMinimumExposureTimeSecs(readMode);
        if (filter == null) return min;
        double res = 3 * filter.exposureTime5050Secs();
        return (res < min) ? min : res;
    }

    public double getMinimumExposureTimeSecs() {
        return getMinimumExposureTimeSecs(getReadMode());
    }

    public static double getMinimumExposureTimeSecs(ReadMode readMode) {
        if (readMode == null) return 0;
        return readMode.minExposureTimeSecs();
    }

    public int getNonDestructiveReads() {
        ReadMode readMode = getReadMode();
        if (readMode == null) return 0;
        return readMode.ndr();
    }

    public int getReadNoise() {
        ReadMode readMode = getReadMode();
        if (readMode == null) return 0;
        return readMode.readNoise();
    }

    /**
     * Time needed to setup the instrument before the Observation
     *
     * @param obs the observation for which the setup time is wanted
     * @return time in seconds
     */
    public double getSetupTime(ISPObservation obs) {
        return 30 * 60;//MCAO setup time: 30m
    }

    /**
     * Time needed to re-setup the instrument before the Observation following a previous full setup.
     *
     * @param obs the observation for which the setup time is wanted
     * @return time in seconds
     */
    public double getReacquisitionTime(ISPObservation obs) {
        return 10 * 60; // 10 mins as defined in REL-1346
    }

    public static CategorizedTime getWheelMoveOverhead() {
        // REL-1103 - 15 seconds for wheel move overhead
        return CategorizedTime.apply(Category.CONFIG_CHANGE, 15000, "Instrument");
    }

        // Predicate that leaves all CategorizedTime except for the offset overhead.
    private static final PredicateOp<CategorizedTime> RM_OFFSET_OVERHEAD = new PredicateOp<CategorizedTime>() {
        @Override public Boolean apply(CategorizedTime ct) {
            return !((ct.category == Category.CONFIG_CHANGE) &&
                     (ct.detail == OffsetOverheadCalculator.DETAIL));
        }
    };

    private static double getOffsetArcsec(Config c, ItemKey k) {
        final String d = (String) c.getItemValue(k); // yes a string :/
        return (d == null) ? 0.0 : Double.parseDouble(d);
    }

    private static boolean isOffset(Config c, Option<Config> prev) {
        final double p1 = getOffsetArcsec(c, OffsetPosBase.TEL_P_KEY);
        final double q1 = getOffsetArcsec(c, OffsetPosBase.TEL_Q_KEY);

        final double p0, q0;
        if (prev.isEmpty()) {
            p0 = 0.0;
            q0 = 0.0;
        } else {
            p0 = getOffsetArcsec(prev.getValue(), OffsetPosBase.TEL_P_KEY);
            q0 = getOffsetArcsec(prev.getValue(), OffsetPosBase.TEL_Q_KEY);
        }
        return (p0 != p1) || (q0 != q1);
    }

    private static boolean isActive(Config c, String prop) {
        final ItemKey k = new ItemKey(SeqConfigNames.TELESCOPE_KEY, prop);
        final GuideOption go = (GuideOption) c.getItemValue(k);
        return (go != null) && go.isActive();
    }

    private static boolean isGuided(Config c) {
        for (GsaoiOdgw odgw : GsaoiOdgw.values()) {
            if (isActive(c, odgw.getSequenceProp())) return true;
        }
        for (Canopus.Wfs wfs : Canopus.Wfs.values()) {
            if (isActive(c, wfs.getSequenceProp())) return true;
        }
        return false;
    }

    private static boolean isGuided(Option<Config> c) {
        return c.isEmpty() ? false : isGuided(c.getValue());
    }

    private static boolean isExpensiveOffset(Config cur, Option<Config> prev) {
        if (!isOffset(cur, prev)) return false;

        final boolean curGuided = isGuided(cur);
        if (curGuided) return true;

        return curGuided != isGuided(prev);
    }

    // REL-1103
    // Get correct offset overhead in the common group.  If a guided offset
    // or a switch from guided to non-guided, it is expensive.  If going from
    // a sky position to another sky position, it counts as a normal offset.
    private CategorizedTimeGroup commonGroup(Config cur, Option<Config> prev) {
        CategorizedTimeGroup ctg = CommonStepCalculator.instance.calc(cur, prev);
        return (isExpensiveOffset(cur, prev)) ?
            ctg.filter(RM_OFFSET_OVERHEAD).add(GUIDED_OFFSET_OVERHEAD_CATEGORIZED_TIME) :
            ctg;
    }


    @Override public CategorizedTimeGroup calc(Config cur, Option<Config> prev) {
        // Add wheel move overhead
        Collection<CategorizedTime> times = new ArrayList<CategorizedTime>();
        if (PlannedTime.isUpdated(cur, prev, Filter.KEY, UtilityWheel.KEY)) {
            times.add(getWheelMoveOverhead());
        }

        // Add exposure time
        double exposureTime = ExposureCalculator.instance.exposureTimeSec(cur);
        int coadds = ExposureCalculator.instance.coadds(cur);
        times.add(CategorizedTime.fromSeconds(Category.EXPOSURE, exposureTime * coadds));

        // Add readout overhead
        int lowNoiseReads = getNonDestructiveReads();
        double readout = 21 + 2.8 * lowNoiseReads * coadds + 6.5 * (coadds - 1); // REL-445
        times.add(CategorizedTime.fromSeconds(Category.READOUT, readout).add(- Category.DHS_OVERHEAD.time)); // REL-1678
        times.add(Category.DHS_OVERHEAD); // REL-1678

        return commonGroup(cur, prev).addAll(times);
    }

    public ParamSet getParamSet(PioFactory factory) {
        ParamSet paramSet = super.getParamSet(factory);

        Pio.addParam(factory, paramSet, FILTER_PROP.getName(), filter.name());
        Pio.addParam(factory, paramSet, READ_MODE_PROP.getName(), readMode.name());
        Pio.addParam(factory, paramSet, PORT_PROP.getName(), port.name());
        Pio.addParam(factory, paramSet, UTILITY_WHEEL_PROP.getName(), utilityWheel.name());
        Pio.addParam(factory, paramSet, ODGW_SIZE_PROP.getName(), odgwSize.name());
        Pio.addParam(factory, paramSet, ROI_PROP.getName(), roi.name());

        return paramSet;
    }

    @Override
    public boolean hasOIWFS() {
        // No OIWFS -- there is a on-detector guide window
        return false;
    }

    public void setParamSet(ParamSet paramSet) {
        super.setParamSet(paramSet);

        String v;
        v = Pio.getValue(paramSet, FILTER_PROP.getName());
        if (v != null) setFilter(Filter.valueOf(v, getFilter()));

        v = Pio.getValue(paramSet, READ_MODE_PROP.getName());
        if (v != null) setReadMode(ReadMode.valueOf(v, getReadMode()));

        v = Pio.getValue(paramSet, PORT_PROP.getName());
        if (v != null) setIssPort(IssPort.valueOf(v));

        v = Pio.getValue(paramSet, UTILITY_WHEEL_PROP.getName());
        if (v != null) setUtilityWheel(UtilityWheel.valueOf(v, getUtilityWheel()));

        v = Pio.getValue(paramSet, ODGW_SIZE_PROP.getName());
        if (v != null) setOdgwSize(OdgwSize.valueOf(v, getOdgwSize()));

        v = Pio.getValue(paramSet, ROI_PROP.getName());
        if (v != null) setRoi(Roi.valueOf(v, getRoi()));

    }

    public ISysConfig getSysConfig() {
        ISysConfig sc = new DefaultSysConfig(SeqConfigNames.INSTRUMENT_CONFIG_NAME);

        sc.putParameter(StringParameter.getInstance(ISPDataObject.VERSION_PROP, getVersion()));
        sc.putParameter(DefaultParameter.getInstance(FILTER_PROP.getName(), getFilter()));
        sc.putParameter(DefaultParameter.getInstance(READ_MODE_PROP.getName(), getReadMode()));
        sc.putParameter(DefaultParameter.getInstance(PORT_PROP, getIssPort()));
        sc.putParameter(DefaultParameter.getInstance(UTILITY_WHEEL_PROP.getName(), getUtilityWheel()));
        sc.putParameter(DefaultParameter.getInstance(ODGW_SIZE_PROP.getName(), getOdgwSize()));
        sc.putParameter(DefaultParameter.getInstance(ROI_PROP.getName(), getRoi()));
        sc.putParameter(DefaultParameter.getInstance(EXPOSURE_TIME_PROP.getName(), getExposureTime()));
        sc.putParameter(DefaultParameter.getInstance(POS_ANGLE_PROP.getName(), getPosAngleDegrees()));
        sc.putParameter(DefaultParameter.getInstance(COADDS_PROP.getName(), getCoadds()));

        return sc;
    }

    public static List<InstConfigInfo> getInstConfigInfo() {
        List<InstConfigInfo> configInfo = new LinkedList<InstConfigInfo>();
        configInfo.add(new InstConfigInfo(FILTER_PROP));
        configInfo.add(new InstConfigInfo(READ_MODE_PROP));
        return configInfo;
    }

    private static final Collection<GuideProbe> GUIDERS = GuideProbeUtil.instance.createCollection(GsaoiOdgw.values());

    public Collection<GuideProbe> getGuideProbes() {
        return GUIDERS;
    }

    public static final ConfigInjector WAVELENGTH_INJECTOR = ConfigInjector.create(
            new ObsWavelengthCalc1<Filter>() {
                @Override
                public PropertyDescriptor descriptor1() {
                    return FILTER_PROP;
                }

                @Override
                public String calcWavelength(Filter f) {
                    return f.formattedWavelength();
                }
            }
    );

    private static final Angle PWFS1_VIG = Angle.arcmins(5.8);
    @Override public Angle pwfs1VignettingClearance() { return PWFS1_VIG; }

}

