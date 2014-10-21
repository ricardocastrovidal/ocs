package edu.gemini.spModel.template;

import edu.gemini.pot.sp.ISPTemplateFolder;
import edu.gemini.pot.sp.ISPTemplateGroup;
import edu.gemini.pot.sp.ISPTemplateParameters;
import edu.gemini.pot.sp.SPComponentType;
import edu.gemini.shared.util.TimeValue;
import edu.gemini.shared.util.immutable.ApplyOp;
import edu.gemini.spModel.data.AbstractDataObject;
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality;
import edu.gemini.spModel.pio.ParamSet;
import edu.gemini.spModel.pio.Pio;
import edu.gemini.spModel.pio.PioFactory;
import edu.gemini.spModel.target.SPTarget;

/**
 * Data object representing template parameters.
 *
 * Note that this class is effectively immutable. Either use the non-empty
 * constructor or call setParamSet() immediately on construction. setParamSet()
 * can be invoked only once.
 */
public final class TemplateParameters extends AbstractDataObject {
    public static final SPComponentType SP_TYPE = SPComponentType.TEMPLATE_PARAMETERS;
    public static final String VERSION = "2015A-1";
    public static final String PARAM_TIME = "time";

    private SPTarget target;
    private SPSiteQuality conditions;
    private TimeValue time;

    public TemplateParameters() {
        setTitle("Template Parameters");
        setType(SP_TYPE);
        setVersion(VERSION);
    }

    public TemplateParameters(ParamSet paramSet) {
        this();
        setParamSet(paramSet);
    }

    public TemplateParameters(SPTarget target, SPSiteQuality conditions, TimeValue timeValue) {
        this();
        this.target     = (SPTarget) target.clone();
        this.conditions = conditions.clone();
        this.time       = timeValue;
    }

    private void checkRef(Object o) {
        if (o == null) throw new IllegalStateException("Not initialized.");
    }
    private void checkRefs() {
        checkRef(target);
        checkRef(conditions);
        checkRef(time);
    }

    public SPTarget getTarget() {
        checkRef(target);
        return (SPTarget) target.clone();
    }

    public SPSiteQuality getSiteQuality() {
        checkRef(conditions);
        return conditions.clone();
    }

    public TimeValue getTime() {
        checkRef(time);
        return time;  // actually immutable
    }

    public ParamSet getParamSet(PioFactory factory) {
        checkRefs();
        final ParamSet ps = super.getParamSet(factory);

        ps.addParamSet(target.getParamSet(factory));
        ps.addParamSet(conditions.getParamSet(factory));
        Pio.addLongParam(factory, ps, PARAM_TIME, time.getMilliseconds());

        return ps;
    }

    public void setParamSet(ParamSet paramSet) {
        if ((target != null) || (conditions != null) || (time != null)) {
            throw new IllegalStateException("Already initialized.");
        }

        super.setParamSet(paramSet);

        final ParamSet targetPs = paramSet.getParamSet(SPTarget.PARAM_SET_NAME);
        target = new SPTarget();
        target.setParamSet(targetPs);

        final ParamSet conditionsPs = paramSet.getParamSet(SPSiteQuality.SP_TYPE.readableStr);
        conditions = new SPSiteQuality();
        conditions.setParamSet(conditionsPs);

        time = TimeValue.millisecondsToTimeValue(Pio.getLongValue(paramSet, PARAM_TIME, 0l), TimeValue.Units.hours);
    }

    @Override
    public boolean equals(Object o) {
        checkRefs();
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final TemplateParameters that = (TemplateParameters) o;
        if (!conditions.equals(that.conditions)) return false;
        if (!target.equals(that.target)) return false;
        return time.equals(that.time);
    }

    @Override
    public int hashCode() {
        checkRefs();
        int result = target.hashCode();
        result = 31 * result + conditions.hashCode();
        result = 31 * result + time.hashCode();
        return result;
    }

    public static void foreach(ISPTemplateFolder folder, ApplyOp<TemplateParameters> op) {
        if (folder != null) {
            for (ISPTemplateGroup g : folder.getTemplateGroups()) {
                for (ISPTemplateParameters p : g.getTemplateParameters()) {
                    op.apply((TemplateParameters) p.getDataObject());
                }
            }
        }
    }
}