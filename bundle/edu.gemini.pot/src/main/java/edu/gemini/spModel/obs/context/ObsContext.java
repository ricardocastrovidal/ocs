//
// $
//

package edu.gemini.spModel.obs.context;

import edu.gemini.pot.sp.*;
import edu.gemini.skycalc.Angle;
import edu.gemini.skycalc.Coordinates;
import edu.gemini.skycalc.Offset;
import edu.gemini.shared.util.immutable.*;
import edu.gemini.spModel.ags.AgsStrategyKey;
import edu.gemini.spModel.core.Site;
import edu.gemini.spModel.data.AbstractDataObject;
import edu.gemini.spModel.gemini.altair.InstAltair;
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality;
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality.Conditions;
import edu.gemini.spModel.obs.SPObservation;
import edu.gemini.spModel.obscomp.SPInstObsComp;
import edu.gemini.spModel.target.SPTarget;
import edu.gemini.spModel.target.env.TargetEnvironment;
import edu.gemini.spModel.target.obsComp.TargetObsComp;
import edu.gemini.spModel.target.offset.OffsetPosBase;
import edu.gemini.spModel.target.offset.OffsetPosList;
import edu.gemini.spModel.target.offset.OffsetUtil;
import edu.gemini.spModel.target.system.CoordinateParam;
import edu.gemini.spModel.telescope.IssPort;
import edu.gemini.spModel.telescope.IssPortProvider;
import edu.gemini.spModel.telescope.PosAngleConstraint;
import edu.gemini.spModel.telescope.PosAngleConstraintAware;
import edu.gemini.spModel.util.SPTreeUtil;


import java.util.*;

/**
 * Configuration context information for an observation.
 */
public final class ObsContext {
    /**
     * Try to get the Site from an ISPObservation.
     *
     * @param observation the observation
     * @return the site if it could be determined, else None
     */
    public static Option<Site> getSiteFromObservation(final ISPObservation observation) {
        // We first try to get the site directly from the instrument.
        final ISPObsComponent obsComponent = SPTreeUtil.findInstrument(observation);
        if (obsComponent != null) {
            final SPInstObsComp instObsComp = (SPInstObsComp) obsComponent.getDataObject();
            final Option<Site> instrumentSite = getSiteFromInstrument(instObsComp);

            if (!instrumentSite.isEmpty())
                return instrumentSite;
        }

        // Otherwise, we try to get the site from the program ID.
        final SPObservationID obsId = observation.getObservationID();
        return (obsId == null) ? None.<Site>instance() : ImOption.apply(obsId.getProgramID().site());
    }

    /**
     * Try to get the Site from an SPInstObsComp.
     *
     * @param instrument the instrument
     * @return the site if it could be determined, else None
     */
    public static Option<Site> getSiteFromInstrument(final SPInstObsComp instrument) {
        if (instrument == null) return None.instance();
        final Set<Site> siteSet = instrument.getSite();
        return siteSet.size() == 1 ? new Some<Site>(siteSet.iterator().next()) : None.<Site>instance();
    }


    /**
     * Creates an observing configuration context for an observation using all
     * the required information.
     *
     * @param targets    set of targets and guide stars
     * @param inst instrument
     * @param sciencePos set of {@link edu.gemini.skycalc.Offset}s at which science data will be taken
     * @param conds site quality conditions
     * @param aoComp an AO component, or null if there is none present
     * @return a new ObsContext
     */
    public static ObsContext create(TargetEnvironment targets,
                                    SPInstObsComp inst,
                                    Conditions conds,
                                    Set<Offset> sciencePos,
                                    AbstractDataObject aoComp) {
        return create(None.<AgsStrategyKey>instance(), targets, inst, getSiteFromInstrument(inst), conds, sciencePos, aoComp);
    }


    /**
     * Creates an observing configuration context for an observation using all
     * the required information.
     *
     * @param targets    set of targets and guide stars
     * @param inst instrument
     * @param sciencePos set of {@link edu.gemini.skycalc.Offset}s at which science data will be taken
     * @param conds site quality conditions
     * @param aoComp an AO component, or null if there is none present
     * @return a new ObsContext
     */
    public static ObsContext create(TargetEnvironment targets,
                                    SPInstObsComp inst,
                                    Option<Site> site,
                                    Conditions conds,
                                    Set<Offset> sciencePos,
                                    AbstractDataObject aoComp) {
        return create(None.<AgsStrategyKey>instance(), targets, inst, site, conds, sciencePos, aoComp);
    }

    public static ObsContext create(Option<AgsStrategyKey> ags,
                                    TargetEnvironment targets,
                                    SPInstObsComp inst,
                                    Option<Site> site,
                                    Conditions conds,
                                    Set<Offset> sciencePos,
                                    AbstractDataObject aoComp) {

        // If no explicit observing positions are provided, use an offset list
        // with a single "offset" to represent the base position.
        Set<Offset> offsets = OffsetUtil.BASE_POS_OFFSET;
        if ((sciencePos != null) && (sciencePos.size() > 0) &&
                (sciencePos != OffsetUtil.BASE_POS_OFFSET)) {
            // Get an unmodifiable copy of the list.
            offsets = Collections.unmodifiableSet(new LinkedHashSet<Offset>(sciencePos));
        }
        if (aoComp != null) {
            return new ObsContext(ags, targets, inst, site, conds, offsets, new Some<AbstractDataObject>(aoComp));
        } else {
            return new ObsContext(ags, targets, inst, site, conds, offsets, None.<AbstractDataObject>instance());
        }
    }

    /**
     * Creates an ObsContext from the given {@link ISPObservation}, assuming
     * it contains at least a target component and an instrument.
     * <p/>
     * <p><em>This method will require numerous remote method calls to the ODB
     * if not called from a database functor.</em></p>
     *
     * @return ObsContext representing this observation or {@link None} if
     *         there is no target or no instrument
     * @ if called from client code and there is a problem
     *                         communicating with the database
     */
    public static Option<ObsContext> create(ISPObservation obs)  {
        Option<ObsData> opt = ObsData.extract(obs);
        if (opt.isEmpty()) return None.instance();

        final TargetObsComp target                 = opt.getValue().target;
        final SPInstObsComp inst                   = opt.getValue().inst;
        final Conditions    conds                  = opt.getValue().conds;
        final Option<AbstractDataObject> aoCompOpt = opt.getValue().aoComp;

        final Option<Site> site     = getSiteFromObservation(obs);
        final TargetEnvironment env = target.getTargetEnvironment();

        final List<OffsetPosList<OffsetPosBase>> posLists;
        posLists = OffsetUtil.allOffsetPosLists(obs);

        OffsetPosList[] posListA = new OffsetPosList[posLists.size()];
        posListA = posLists.toArray(posListA);
        final Set<Offset> offsets = OffsetUtil.getSciencePositions(posListA);

        final SPObservation spObs = (SPObservation) obs.getDataObject();
        return new Some<ObsContext>(ObsContext.create(spObs.getSelectedAgsStrategy(), env, inst, site, conds,
                offsets, aoCompOpt.getOrNull()));
    }

    // Fish out the target, instrument and AO component data objects
    private static class ObsData {
        public final TargetObsComp target;
        public final SPInstObsComp inst;
        public final Conditions conds;
        public final Option<AbstractDataObject> aoComp;

        ObsData(TargetObsComp target, SPInstObsComp inst, Conditions conds, Option<AbstractDataObject> aoComp) {
            this.target = target;
            this.inst   = inst;
            this.conds  = conds;
            this.aoComp = aoComp;
        }

        static Option<ObsData> extract(ISPObservation obs)  {
            TargetObsComp      target = null;
            SPInstObsComp        inst = null;
            Conditions          conds = Conditions.WORST;
            AbstractDataObject aoComp = null;

            for (ISPObsComponent obsComp : obs.getObsComponents()) {
                SPComponentBroadType type = obsComp.getType().broadType;
                if (type.equals(TargetObsComp.SP_TYPE.broadType)) {
                    target = (TargetObsComp) obsComp.getDataObject();
                } else if (type.equals(SPComponentBroadType.INSTRUMENT)) {
                    inst = (SPInstObsComp) obsComp.getDataObject();
                } else if (type.equals(SPSiteQuality.SP_TYPE.broadType)) {
                    conds = ((SPSiteQuality) obsComp.getDataObject()).conditions();
                } else if (type.equals(InstAltair.SP_TYPE.broadType)) {
                    aoComp = (AbstractDataObject) obsComp.getDataObject();
                }
            }

            if ((target == null) || (inst == null) || (conds == null)) return None.instance();
            Option<AbstractDataObject> aoOpt = (aoComp == null) ? None.<AbstractDataObject>instance() : new Some<AbstractDataObject>(aoComp);
            return new Some<ObsData>(new ObsData(target, inst, conds, aoOpt));
        }
    }

    private final Option<AgsStrategyKey> agsStrategy;
    private final TargetEnvironment targets;
    private final SPInstObsComp inst;
    private final Conditions conds;
    private final Set<Offset> sciencePositions;
    private final Option<AbstractDataObject> aoCompOpt;
    private final Option<Site> site;

    private ObsContext(Option<AgsStrategyKey> ags, TargetEnvironment targets, SPInstObsComp inst, Option<Site> site,
                       Conditions conds, Set<Offset> sciencePositions, Option<AbstractDataObject> aoCompOpt) {
        this.agsStrategy      = ags;
        this.targets          = targets;
        this.inst             = (SPInstObsComp) inst.clone();
        this.site             = site;
        this.conds            = conds;
        this.sciencePositions = sciencePositions;
        this.aoCompOpt        = aoCompOpt;
    }

    private ObsContext(Option<AgsStrategyKey> ags, TargetEnvironment targets, SPInstObsComp inst, Option<Site> site,
                       Conditions conds, Set<Offset> sciencePositions, Option<AbstractDataObject> aoCompOpt,
                       Angle posAngle) {
        this(ags, targets, inst, site, conds, sciencePositions, aoCompOpt);
        this.inst.setPosAngle(posAngle.toDegrees().getMagnitude());
    }
    private ObsContext(Option<AgsStrategyKey> ags, TargetEnvironment targets, SPInstObsComp inst, Option<Site> site,
                       Conditions conds, Set<Offset> sciencePositions, Option<AbstractDataObject> aoCompOpt,
                       IssPort port) {
        this(ags, targets, inst, site, conds, sciencePositions, aoCompOpt);
        if (this.inst instanceof IssPortProvider) {
            ((IssPortProvider) this.inst).setIssPort(port);
        }
    }

    public Option<AgsStrategyKey> getSelectedAgsStrategy() {
        return agsStrategy;
    }

    public ObsContext withSelectedAgsStrategy(Option<AgsStrategyKey> s) {
        if (s.equals(agsStrategy)) return this;
        return new ObsContext(s, targets, inst, site, conds, sciencePositions, aoCompOpt);
    }

    public TargetEnvironment getTargets() {
        return targets;
    }

    public ObsContext withTargets(TargetEnvironment targets) {
        if (targets.equals(this.targets)) return this;
        return new ObsContext(agsStrategy, targets, inst, site, conds, sciencePositions, aoCompOpt);
    }

    public Coordinates getBaseCoordinates() {
        SPTarget target = targets.getBase();
        double raDeg = target.getC1().getAs(CoordinateParam.Units.DEGREES);
        double decDeg = target.getC2().getAs(CoordinateParam.Units.DEGREES);
        return new Coordinates(raDeg, decDeg);
    }

    public Angle getPositionAngle() {
        double deg = inst.getPosAngle();
        return new Angle(deg, Angle.Unit.DEGREES);
    }

    public ObsContext withPositionAngle(Angle angle) {
        if (angle.equals(getPositionAngle())) return this;
        return new ObsContext(agsStrategy, targets, inst, site, conds, sciencePositions, aoCompOpt, angle);
    }

    public PosAngleConstraint getPosAngleConstraint() {
        // the pos angle constraint for instruments that are not PosAngleConstraintAware
        // should default to FIXED fo selection strategy
        return getPosAngleConstraint(PosAngleConstraint.FIXED);
    }

    public PosAngleConstraint getPosAngleConstraint(PosAngleConstraint defaultPac) {
        // allow a default value to be passed down which should be used for instruments
        // that are not PosAngleConstraintAware, this allows to make UNKNOWN the default
        // for estimation for instruments that are not PosAngleConstraintAware
        if (inst instanceof PosAngleConstraintAware) {
            return ((PosAngleConstraintAware) inst).getPosAngleConstraint();
        } else {
            return defaultPac;
        }
    }

    public IssPort getIssPort() {
        IssPort port = IssPort.DEFAULT;
        if (inst instanceof IssPortProvider) {
            port = ((IssPortProvider) inst).getIssPort();
        }
        return port;
    }

    public ObsContext withIssPort(IssPort port) {
        if (getIssPort() == port) return this;
        return new ObsContext(agsStrategy, targets, inst, site, conds, sciencePositions, aoCompOpt, port);
    }

    public SPInstObsComp getInstrument(){
        return (SPInstObsComp) inst.clone();
    }

    public ObsContext withInstrument(SPInstObsComp inst) {
        return create(agsStrategy, targets, inst, site, conds, sciencePositions, aoCompOpt.getOrNull());
    }

    public Option<Site> getSite() { return site; }

    public ObsContext withSite(final Option<Site> site) {
        return new ObsContext(agsStrategy, targets, inst, site, conds, sciencePositions, aoCompOpt);
    }

    public Conditions getConditions() {
        return conds;
    }

    public ObsContext withConditions(Conditions conds) {
        if (getConditions().equals(conds)) return this;
        return new ObsContext(agsStrategy, targets, inst, site, conds, sciencePositions, aoCompOpt);
    }

    public Option<AbstractDataObject> getAOComponent(){
        return aoCompOpt;
    }

    public ObsContext withAOComponent(AbstractDataObject aoCompOpt){
        return new ObsContext(agsStrategy, targets,  inst, site, conds, sciencePositions, new Some<AbstractDataObject>(aoCompOpt));
    }


    public Set<Offset> getSciencePositions() {
        return sciencePositions;
    }

    public ObsContext withSciencePositions(Set<Offset> sciencePos) {
        if (sciencePos.equals(this.sciencePositions)) return this;
        return create(agsStrategy, targets, inst, site, conds, sciencePos, aoCompOpt.getOrNull());
    }

    public String toString() {
        return String.format("ObsContext [posAngle=%8.4f, issPort=%4s, targets=[%s], offsets=[%s], aoComponent=[%s]",
                getPositionAngle().toDegrees().getMagnitude(), getIssPort().shortName(), targets, sciencePositions,
                aoCompOpt.isEmpty() ? "None" : aoCompOpt.getValue().getNarrowType());
    }
}