// Copyright 1997-2000
// Association for Universities for Research in Astronomy, Inc.,
// Observatory Control System, Gemini Telescopes Project.
// See the file COPYRIGHT for complete details.
//
// $Id: SPSiteQuality.java 45640 2012-05-30 17:57:03Z nbarriga $
//
package edu.gemini.spModel.gemini.obscomp;

import edu.gemini.pot.sp.SPComponentType;
import edu.gemini.shared.skyobject.Magnitude;
import edu.gemini.shared.util.immutable.MapOp;
import edu.gemini.shared.util.immutable.None;
import edu.gemini.shared.util.immutable.Option;
import edu.gemini.shared.util.immutable.Some;
import edu.gemini.spModel.data.AbstractDataObject;
import edu.gemini.spModel.data.property.PropertyProvider;
import edu.gemini.spModel.data.property.PropertySupport;
import edu.gemini.spModel.pio.ParamSet;
import edu.gemini.spModel.pio.Pio;
import edu.gemini.spModel.pio.PioFactory;
import edu.gemini.spModel.type.DisplayableSpType;
import edu.gemini.spModel.type.ObsoletableSpType;
import edu.gemini.spModel.type.SequenceableSpType;
import edu.gemini.spModel.type.SpTypeUtil;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.util.*;

import static edu.gemini.shared.skyobject.Magnitude.Band.R;

/**
 * Site Quality observation component.
 */
public class SPSiteQuality extends AbstractDataObject implements PropertyProvider  {
    // for serialization
    private static final long serialVersionUID = 3L;


    public static final SPComponentType SP_TYPE = SPComponentType.SCHEDULING_CONDITIONS;

    /**
     * SPSiteQuality owns a list of TimingWindow objects specifying when the target is observable.
     * By convention, an empty list signifies that the target is always observable, which is the same
     * as the list containing the ALWAYS static instance.
     * @author rnorris
     */
    public static class TimingWindow implements Serializable, Cloneable {

		private static final long serialVersionUID = 1L;

		public static final int WINDOW_REMAINS_OPEN_FOREVER = -1;


		public static final int REPEAT_FOREVER = -1;
		public static final int REPEAT_NEVER = 0;

		private static final long MS_PER_SECOND = 1000;
		private static final long MS_PER_MINUTE = MS_PER_SECOND * 60;
		private static final long MS_PER_HOUR = MS_PER_MINUTE * 60;

		public static TimingWindow ALWAYS = new TimingWindow(Long.MIN_VALUE, Long.MAX_VALUE, REPEAT_NEVER, 0);

		private static final String NAME = "timing-window";

		private static final String START_PROP = "start";
		private static final String DURATION_PROP = "duration";
		private static final String REPEAT_PROP = "repeat";
		private static final String PERIOD_PROP = "period";

		// All times and durations in ms
		private final long start, duration, period;
		private final int repeat;

		public TimingWindow(long start, long duration, int repeat, long period) {
			this.start = start;
			this.duration = duration;
			this.repeat = repeat;
			this.period = period;
			assert repeat >= -1;
		}

		public TimingWindow() {
			this(System.currentTimeMillis(), 24 * MS_PER_HOUR, 0, 0);
		}

		TimingWindow(ParamSet params) {
			this(Pio.getLongValue(params, START_PROP, 0),
				 Pio.getLongValue(params, DURATION_PROP, 0),
				 Pio.getIntValue(params, REPEAT_PROP, 0),
				 Pio.getLongValue(params, PERIOD_PROP, 0));
		}

		public long getDuration() {
			return duration;
		}

		public long getPeriod() {
			return period;
		}

		public int getRepeat() {
			return repeat;
		}

		public long getStart() {
			return start;
		}

		ParamSet getParamSet(PioFactory factory) {
			ParamSet params = factory.createParamSet(NAME);
			Pio.addLongParam(factory, params, START_PROP, start);
			Pio.addLongParam(factory, params, DURATION_PROP, duration);
			Pio.addIntParam(factory, params, REPEAT_PROP, repeat);
			Pio.addLongParam(factory, params, PERIOD_PROP, period);
			return params;
		}

		@Override
		public String toString() {
			return String.format("{%d %d %d %d}", start, duration, repeat, period);
		}

		public TimingWindow clone() {
			try {
				return (TimingWindow) super.clone();
			} catch (CloneNotSupportedException e) {
				throw new Error("This was supposed to be impossible.");
			}
		}

    }

    static class TimingWindowList extends LinkedList<TimingWindow> {

		private static final long serialVersionUID = 2L;
		private static final String NAME = "timing-window-list";

		ParamSet getParamSet(PioFactory factory) {
			ParamSet params = factory.createParamSet(NAME);
			for (TimingWindow tw: this)
				params.addParamSet(tw.getParamSet(factory));
			return params;
		}

		void setParamSet(ParamSet params) {
			clear();
			if (params != null) {
				for (ParamSet ps: params.getParamSets())
					add(new TimingWindow(ps));
			}
		}

		@Override
		public TimingWindowList clone() {
			TimingWindowList ret = new TimingWindowList();
			for (TimingWindow tw: this)
				ret.add(tw.clone());
			return ret;
		}

    }

    @Override
    public SPSiteQuality clone() {
    	SPSiteQuality ret = (SPSiteQuality) super.clone();
    	ret._timingWindows = _timingWindows.clone();
    	return ret;
    }

    public interface PercentageContainer {
        byte getPercentage();
    }

    private static <T extends PercentageContainer> Option<T> read(String strVal, T[] values) {
        if (strVal.length() < 3) return None.instance();

        String tail = strVal.substring(2);
        if ("Any".equalsIgnoreCase(tail)) tail = "100";

        byte perc;
        try {
            perc = Byte.valueOf(tail);
        } catch (NumberFormatException ex) {
            return None.instance();
        }

        for (T val : values) {
            if (val.getPercentage() == perc) return new Some<T>(val);
        }
        return None.instance();
    }

    /**
     * Sky Background Options.
     */
    public static enum SkyBackground implements DisplayableSpType, SequenceableSpType, PercentageContainer {

        PERCENT_20("20%/Darkest", 20, 21.37),
        PERCENT_50("50%/Dark", 50, 20.78),
        PERCENT_80("80%/Grey", 80, 19.61) { public Magnitude adjust(Magnitude mag) { return adjustIf(R, mag, -0.3); } },
        ANY("Any/Bright", 100, 0)         { public Magnitude adjust(Magnitude mag) { return adjustIf(R, mag, -0.5); }
        };

        /** The default SkyBackground value **/
        public static SkyBackground DEFAULT = ANY;

        private final String _displayValue;
        private final byte _percentage;
        private final double _maxBrightness; // in vMag, smaller is brighter

        private SkyBackground(String displayValue, int percentage, double maxBrightness) {
        	_percentage = (byte) percentage;
            _displayValue = displayValue;
            _maxBrightness = maxBrightness;
            assert _percentage >= 0 && _percentage <= 100;
        }

        public byte getPercentage() {
        	return _percentage;
        }

        /**
         * Returns the maximum brightness for this background percentile in vMag.
         * Note that smaller values are brighter. Wacky astronomers, go figure.
         */
        public double getMaxBrightness() {
			return _maxBrightness;
		}

        public String displayValue() {
            return _displayValue;
        }

        public String sequenceValue() {
            return Byte.toString(_percentage);
        }

        public Magnitude adjust(Magnitude mag) { return mag; }


        /** Return a SkyBackground by name **/
        public static SkyBackground getSkyBackground(String name) {
            return getSkyBackground(name, DEFAULT);
        }

        /** Return a SkyBackground by name with a value to return upon error **/
        public static SkyBackground getSkyBackground(String name, SkyBackground nvalue) {
            return SpTypeUtil.oldValueOf(SkyBackground.class, name, nvalue);
        }

        public String toString() {
            return (this == ANY) ? "SBAny" : String.format("SB%2d", getPercentage());
        }

        public static Option<SkyBackground> read(String s) {
            return SPSiteQuality.read(s, values());
        }
    }

    /**
     * Get a specific site quality option from its percentage
     *
     * @param c The class representing the site quality attribute (CloudCover, ImageQuality, etc...), must implement PercentageContainer
     * @param percentage The percentage container
     * @param <T> The type to return, and Enum that implements PercentageContainer
     * @return An optional enum instance
     */
    public static  <T extends Enum<T> & PercentageContainer> Option<T> getEnumFromPercentage(Class<T> c, int percentage){
        for(T t:c.getEnumConstants()){
            if(t.getPercentage() == percentage){
                return new Some(t);
            }
        }
        return None.<T>instance();
    }

    /**
     * Cloud Cover Options.
     */
    public static enum CloudCover implements DisplayableSpType, ObsoletableSpType, SequenceableSpType, PercentageContainer {
        PERCENT_20("20%", 20),
        PERCENT_50("50%/Clear", 50),
        PERCENT_70("70%/Cirrus", 70) { public Magnitude adjust(Magnitude mag) { return mag.add(-0.3); } },
        PERCENT_80("80%/Cloudy", 80) { public Magnitude adjust(Magnitude mag) { return mag.add(-1.0); } },
        PERCENT_90("90%", 90) { public Magnitude adjust(Magnitude mag) { return mag.add(-3.0); } },
        ANY("Any", 100)       { public Magnitude adjust(Magnitude mag) { return mag.add(-3.0); } },
        ;


        /** The default CloudCover value **/
        public static CloudCover DEFAULT = ANY;

        private final String _displayValue;
        private final byte _percentage;

        private CloudCover(String displayValue, int percentage) {
        	_percentage = (byte) percentage;
            _displayValue = displayValue;
            assert _percentage >= 0 && _percentage <= 100;
        }

        public byte getPercentage() {
        	return _percentage;
        }


        public String displayValue() {
            return _displayValue;
        }

        public String sequenceValue() {
            return Byte.toString(_percentage);
        }

        public Magnitude adjust(Magnitude mag) { return mag; }

        /** Return a CloudCover by name **/
        public static CloudCover getCloudCover(String name) {
            return getCloudCover(name, DEFAULT);
        }



        /** Return a CloudCover by name with a value to return upon error **/
        public static CloudCover getCloudCover(String name, CloudCover nvalue) {
            return SpTypeUtil.oldValueOf(CloudCover.class, name, nvalue);
        }

        public boolean isObsolete() {
        	return (this == PERCENT_20) || (this == PERCENT_90);
        }

        public String toString() {
            return (this == ANY) ? "CCAny" : String.format("CC%2d", getPercentage());
        }

        public static Option<CloudCover> read(String s) {
            return SPSiteQuality.read(s, values());
        }
    }

    /**
     * Image Quality Options.
     */
    public static enum ImageQuality implements DisplayableSpType, SequenceableSpType, PercentageContainer {
        PERCENT_20("20%/Best", 20) { public Magnitude adjust(Magnitude mag) { return adjustIf(R, mag,  0.5); }},
        PERCENT_70("70%/Good", 70),
        PERCENT_85("85%/Poor", 85) { public Magnitude adjust(Magnitude mag) { return adjustIf(R, mag, -0.5); }},
        ANY("Any", 100)       { public Magnitude adjust(Magnitude mag) { return adjustIf(R, mag, -1.0); }},
        ;

        /** The default ImageQuality value **/
        public static ImageQuality DEFAULT = ANY;

        private final String _displayValue;
        private final byte _percentage;

        private ImageQuality(String displayValue, int percentage) {
        	_percentage = (byte) percentage;
            _displayValue = displayValue;
            assert _percentage >= 0 && _percentage <= 100;
        }

        public byte getPercentage() {
        	return _percentage;
        }


        public String displayValue() {
            return _displayValue;
        }

        public String sequenceValue() {
            return Byte.toString(_percentage);
        }

        public Magnitude adjust(Magnitude mag) { return mag; }

        /** Return a ImageQuality by name **/
        public static ImageQuality getImageQuality(String name) {
            return getImageQuality(name, DEFAULT);
        }

        /** Return a ImageQuality by name with a value to return upon error **/
        public static ImageQuality getImageQuality(String name, ImageQuality nvalue) {
            // Note the following is a temporary patch.  If name has a value of 50, it means 70
            // if we have a value of 80 it means 85.  This should go away after the next version
            if (name.equals("50%")) return PERCENT_70;
            if (name.equals("80")) return PERCENT_85;

            return SpTypeUtil.oldValueOf(ImageQuality.class, name, nvalue);
        }

        public String toString() {
            return (this == ANY) ? "IQAny" : String.format("IQ%2d", getPercentage());
        }

        public static Option<ImageQuality> read(String s) {
            return SPSiteQuality.read(s, values());
        }
    }

    /**
     * Water Vapor Options.
     */
    public static enum WaterVapor implements DisplayableSpType, SequenceableSpType, PercentageContainer {
        PERCENT_20("20%/Low", 20),
        PERCENT_50("50%/Median", 50),
        PERCENT_80("80%/High", 80),
        ANY("Any", 100),
        ;

        /** The default WaterVapor value **/
        public static WaterVapor DEFAULT = ANY;

        private final String _displayValue;
        private final byte _percentage;

        private WaterVapor(String displayValue, int percentage) {
        	_percentage = (byte) percentage;
            _displayValue = displayValue;
            assert _percentage >= 0 && _percentage <= 100;
        }

        public byte getPercentage() {
        	return _percentage;
        }


        public String displayValue() {
            return  _displayValue;
        }

        public String sequenceValue() {
            return Byte.toString(_percentage);
        }

        /** Return a WaterVapor by name **/
        public static WaterVapor getWaterVapor(String name) {
            return getWaterVapor(name, DEFAULT);
        }

        /** Return a WaterVapor by name with a value to return upon error **/
        public static WaterVapor getWaterVapor(String name, WaterVapor nvalue) {
            return SpTypeUtil.oldValueOf(WaterVapor.class, name, nvalue);
        }

        public String toString() {
            return (this == ANY) ? "WVAny" : String.format("WV%2d", getPercentage());
        }

        public static Option<WaterVapor> read(String s) {
            return SPSiteQuality.read(s, values());
        }
    }

    /**
     * Elevation Constraint Options
     */
    public static enum ElevationConstraintType implements DisplayableSpType {

    	NONE("None", 0, 0, 0, 0),
    	HOUR_ANGLE("Hour Angle", -5.5, 5.5, -5.0, 5.0),
    	AIRMASS("Airmass", 1.0, 3.0, 1.0, 2.0),

        ;

        public static ElevationConstraintType DEFAULT = NONE;

        private final String _displayValue;
        private final double _min, _max;
        private final double _defaultMin, _defaultMax;

        private ElevationConstraintType(String displayValue, double min, double max, double defaultMin, double defaultMax) {
            _displayValue = displayValue;
            _min = min;
            _max = max;
            _defaultMax = defaultMax;
            _defaultMin = defaultMin;
        }

        public String displayValue() {
            return  _displayValue;
        }

        public double getDefaultMin() {
			return _defaultMin;
		}

        public double getDefaultMax() {
			return _defaultMax;
		}

        public double getMax() {
			return _max;
		}

        public double getMin() {
			return _min;
		}

        public static ElevationConstraintType getElevationConstraintType(String name) {
        	try {
        		return valueOf(name);
        	} catch (IllegalArgumentException iae) {
        		return DEFAULT;
        	}
        }

    }

    private static Magnitude adjustIf(Magnitude.Band band, Magnitude mag, double amount) {
        return (mag.getBand() == band) ? mag.add(amount) : mag;
    }

    public static final class Conditions implements Serializable {
        public static final Conditions BEST    = new Conditions(CloudCover.PERCENT_20, ImageQuality.PERCENT_20, SkyBackground.PERCENT_20, WaterVapor.PERCENT_20);
        public static final Conditions NOMINAL = new Conditions(CloudCover.PERCENT_50, ImageQuality.PERCENT_70, SkyBackground.PERCENT_50, WaterVapor.ANY);
        public static final Conditions WORST   = new Conditions(CloudCover.ANY,        ImageQuality.ANY,        SkyBackground.ANY,        WaterVapor.ANY);

        public final CloudCover cc;
        public final ImageQuality iq;
        public final SkyBackground sb;
        public final WaterVapor wv;

        public Conditions(CloudCover cc, ImageQuality iq, SkyBackground sb, WaterVapor wv) {
            this.cc = cc;
            this.iq = iq;
            this.sb = sb;
            this.wv = wv;
        }

        public Conditions cc(CloudCover ncc)    { return new Conditions(ncc, iq,  sb,  wv); }
        public Conditions iq(ImageQuality niq)  { return new Conditions(cc, niq,  sb,  wv); }
        public Conditions sb(SkyBackground nsb) { return new Conditions(cc,  iq, nsb,  wv); }
        public Conditions wv(WaterVapor nwv)    { return new Conditions(cc,  iq,  sb, nwv); }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Conditions that = (Conditions) o;
            if (cc != that.cc) return false;
            if (iq != that.iq) return false;
            if (sb != that.sb) return false;
            if (wv != that.wv) return false;
            return true;
        }

        @Override public int hashCode() {
            int result = cc.hashCode();
            result = 31 * result + iq.hashCode();
            result = 31 * result + sb.hashCode();
            result = 31 * result + wv.hashCode();
            return result;
        }

        /**
         * Adjust the given magnitude limit for the conditions.
         *
         * @param mag magnitude at nominal conditions
         *
         * @return adjusted magnitudue limit for these conditions
         */
        public Magnitude adjust(Magnitude mag) {
            return cc.adjust(iq.adjust(sb.adjust(mag)));
        }

        public MapOp<Magnitude, Magnitude> magAdjustOp() {
            return new MapOp<Magnitude, Magnitude>() {
                @Override public Magnitude apply(Magnitude magnitude) {
                    return adjust(magnitude);
                }
            };
        }

        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append(cc).append(", ").append(iq).append(", ").append(sb).append(", ").append(wv);
            return buf.toString();
        }
    }


    public static final PropertyDescriptor CLOUD_COVER_PROP;
    public static final PropertyDescriptor IMAGE_QUALITY_PROP;
    public static final PropertyDescriptor SKY_BACKGROUND_PROP;
    public static final PropertyDescriptor WATER_VAPOR_PROP;
    public static final PropertyDescriptor ELEVATION_CONSTRAINT_TYPE_PROP;
    public static final PropertyDescriptor ELEVATION_CONSTRAINT_MIN_PROP;
    public static final PropertyDescriptor ELEVATION_CONSTRAINT_MAX_PROP;
    public static final PropertyDescriptor TIMING_WINDOWS_PROP;

    private static final Map<String, PropertyDescriptor> PRIVATE_PROP_MAP = new TreeMap<String, PropertyDescriptor>();
    public static final Map<String, PropertyDescriptor> PROPERTY_MAP = Collections.unmodifiableMap(PRIVATE_PROP_MAP);

    static {
        CLOUD_COVER_PROP    = PropertySupport.init("CloudCover", SPSiteQuality.class, true, false);
        IMAGE_QUALITY_PROP  = PropertySupport.init("ImageQuality", SPSiteQuality.class, true, false);
        SKY_BACKGROUND_PROP = PropertySupport.init("SkyBackground", SPSiteQuality.class, true, false);
        WATER_VAPOR_PROP    = PropertySupport.init("WaterVapor", SPSiteQuality.class, true, false);
        ELEVATION_CONSTRAINT_TYPE_PROP = PropertySupport.init("ElevationConstraintType", SPSiteQuality.class, true, false);
        ELEVATION_CONSTRAINT_MIN_PROP = PropertySupport.init("ElevationConstraintMin", SPSiteQuality.class, true, false);
        ELEVATION_CONSTRAINT_MAX_PROP = PropertySupport.init("ElevationConstraintMax", SPSiteQuality.class, true, false);
        TIMING_WINDOWS_PROP = PropertySupport.init("TimingWindows", SPSiteQuality.class, false, false);

        PRIVATE_PROP_MAP.put(CLOUD_COVER_PROP.getName(),    CLOUD_COVER_PROP);
        PRIVATE_PROP_MAP.put(IMAGE_QUALITY_PROP.getName(),  IMAGE_QUALITY_PROP);
        PRIVATE_PROP_MAP.put(SKY_BACKGROUND_PROP.getName(), SKY_BACKGROUND_PROP);
        PRIVATE_PROP_MAP.put(WATER_VAPOR_PROP.getName(),    WATER_VAPOR_PROP);
        PRIVATE_PROP_MAP.put(ELEVATION_CONSTRAINT_TYPE_PROP.getName(), ELEVATION_CONSTRAINT_TYPE_PROP);
        PRIVATE_PROP_MAP.put(ELEVATION_CONSTRAINT_MIN_PROP.getName(), ELEVATION_CONSTRAINT_MIN_PROP);
        PRIVATE_PROP_MAP.put(ELEVATION_CONSTRAINT_MAX_PROP.getName(), ELEVATION_CONSTRAINT_MAX_PROP);
        PRIVATE_PROP_MAP.put(TIMING_WINDOWS_PROP.getName(), TIMING_WINDOWS_PROP);
    }

    private Conditions conditions = Conditions.WORST;
    private ElevationConstraintType _elevationConstraintType = ElevationConstraintType.DEFAULT;
    private double _elevationConstraintMin = 0;
    private double _elevationConstraintMax = 0;

    private TimingWindowList _timingWindows = new TimingWindowList();

    /**
     * Default constructor.  Initialize the component type.
     */
    public SPSiteQuality() {
        super(SP_TYPE);
    }

    public Map<String, PropertyDescriptor> getProperties() {
        return PROPERTY_MAP;
    }

    public Conditions conditions() { return conditions; }
    public Magnitude adjust(Magnitude mag) { return conditions.adjust(mag); }

    /**
     * Set the sky.
     */
    public void setSkyBackground(SkyBackground newValue) {
        SkyBackground oldValue = conditions.sb;
        if (oldValue != newValue) {
            conditions = conditions.sb(newValue);
            firePropertyChange(SKY_BACKGROUND_PROP.getName(), oldValue, newValue);
        }
    }

    /**
     * Get the sky.
     */
    public SkyBackground getSkyBackground() { return conditions.sb; }

    /**
     * Set the cloud cover
     */
    public void setCloudCover(CloudCover newValue) {
        CloudCover oldValue = conditions.cc;
        if (oldValue != newValue) {
            conditions = conditions.cc(newValue);
            firePropertyChange(CLOUD_COVER_PROP.getName(), oldValue, newValue);
        }
    }

    /**
     * Get the cloud cover
     */
    public CloudCover getCloudCover() { return conditions.cc; }

    /**
     * Set the image quality.
     */
    public void setImageQuality(ImageQuality newValue) {
        ImageQuality oldValue = conditions.iq;
        if (newValue != oldValue) {
            conditions = conditions.iq(newValue);
            firePropertyChange(IMAGE_QUALITY_PROP.getName(), oldValue, newValue);
        }
    }

    /**
     * Get the image quality.
     */
    public ImageQuality getImageQuality() { return conditions.iq; }


    /**
     * Set Water Vapor
     */
    public void setWaterVapor(WaterVapor newValue) {
        WaterVapor oldValue = conditions.wv;
        if (oldValue != newValue) {
            conditions = conditions.wv(newValue);
            firePropertyChange(WATER_VAPOR_PROP.getName(), oldValue, newValue);
        }
    }

    /**
     * Get the Water Vapor.
     */
    public WaterVapor getWaterVapor() { return conditions.wv; }

    public ElevationConstraintType getElevationConstraintType() {
		return _elevationConstraintType;
	}

	public void setElevationConstraintType(ElevationConstraintType constraintType) {
		ElevationConstraintType prev = _elevationConstraintType;
		_elevationConstraintType = constraintType;
		firePropertyChange(ELEVATION_CONSTRAINT_TYPE_PROP.getName(), prev, constraintType);
	}

	public double getElevationConstraintMin() {
		return _elevationConstraintMin;
	}

	public void setElevationConstraintMin(double min) {
		double prev = _elevationConstraintMin;
		_elevationConstraintMin = min;
		firePropertyChange(ELEVATION_CONSTRAINT_MIN_PROP.getName(), prev, min);
	}

	public double getElevationConstraintMax() {
		return _elevationConstraintMax;
	}

	public void setElevationConstraintMax(double max) {
		double prev = _elevationConstraintMax;
		_elevationConstraintMax = max;
		firePropertyChange(ELEVATION_CONSTRAINT_MAX_PROP.getName(), prev, max);
	}

	public List<TimingWindow> getTimingWindows() {
		return Collections.unmodifiableList(_timingWindows);
	}

	public void setTimingWindows(List<TimingWindow> windows) {
		List<TimingWindow> prev = Collections.unmodifiableList(new ArrayList<TimingWindow>(_timingWindows));
		_timingWindows.clear();
		_timingWindows.addAll(windows);
		firePropertyChange(TIMING_WINDOWS_PROP.getName(), prev, getTimingWindows());
	}

	public void addTimingWindow(TimingWindow tw) {
		List<TimingWindow> prev = Collections.unmodifiableList(new ArrayList<TimingWindow>(_timingWindows));
		_timingWindows.add(tw);
		firePropertyChange(TIMING_WINDOWS_PROP.getName(), prev, getTimingWindows());
	}

	public void removeTimingWindow(TimingWindow tw) {
		List<TimingWindow> prev = Collections.unmodifiableList(new ArrayList<TimingWindow>(_timingWindows));
		if (_timingWindows.remove(tw)) {
			firePropertyChange(TIMING_WINDOWS_PROP.getName(), prev, getTimingWindows());
		}
	}

	/*
     * Return a parameter set describing the current state of this object.
     * @param factory
     */
    public ParamSet getParamSet(PioFactory factory) {
        ParamSet paramSet = super.getParamSet(factory);

        Pio.addParam(factory, paramSet, CLOUD_COVER_PROP, conditions.cc.name());
        Pio.addParam(factory, paramSet, IMAGE_QUALITY_PROP, conditions.iq.name());
        Pio.addParam(factory, paramSet, SKY_BACKGROUND_PROP, conditions.sb.name());
        Pio.addParam(factory, paramSet, WATER_VAPOR_PROP, conditions.wv.name());
        Pio.addParam(factory, paramSet, ELEVATION_CONSTRAINT_TYPE_PROP, _elevationConstraintType.name());
        Pio.addDoubleParam(factory, paramSet, ELEVATION_CONSTRAINT_MIN_PROP.getName(), _elevationConstraintMin);
        Pio.addDoubleParam(factory, paramSet, ELEVATION_CONSTRAINT_MAX_PROP.getName(), _elevationConstraintMax);

        paramSet.addParamSet(_timingWindows.getParamSet(factory));

        return paramSet;
    }

    /**
     * Set the state of this object from the given parameter set.
     */
    public void setParamSet(ParamSet paramSet) {
        super.setParamSet(paramSet);

        String v = Pio.getValue(paramSet, CLOUD_COVER_PROP.getName());
        if (v != null) {
            setCloudCover(CloudCover.getCloudCover(v));
        }
        v = Pio.getValue(paramSet, IMAGE_QUALITY_PROP.getName());
        if (v != null) {
            setImageQuality(ImageQuality.getImageQuality(v));
        }
        v = Pio.getValue(paramSet, SKY_BACKGROUND_PROP.getName());
        if (v != null) {
            setSkyBackground(SkyBackground.getSkyBackground(v));
        }
        v = Pio.getValue(paramSet, WATER_VAPOR_PROP.getName());
        if (v != null) {
            setWaterVapor(WaterVapor.getWaterVapor(v));
        }
        v = Pio.getValue(paramSet, ELEVATION_CONSTRAINT_TYPE_PROP.getName());
        if (v != null) {
            setElevationConstraintType(ElevationConstraintType.getElevationConstraintType(v));
        }

        setElevationConstraintMin(Pio.getDoubleValue(paramSet, ELEVATION_CONSTRAINT_MIN_PROP.getName(), 0.0));
        setElevationConstraintMax(Pio.getDoubleValue(paramSet, ELEVATION_CONSTRAINT_MAX_PROP.getName(), 0.0));

        _timingWindows.setParamSet(paramSet.getParamSet(TimingWindowList.NAME));

    }
}