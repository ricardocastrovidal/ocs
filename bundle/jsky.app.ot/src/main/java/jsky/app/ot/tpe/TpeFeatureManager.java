// Copyright 1997 Association for Universities for Research in Astronomy, Inc.,
// Observatory Control System, Gemini Telescopes Project.
// See the file COPYRIGHT for complete details.
//
// $Id: TpeFeatureManager.java 45662 2012-05-31 02:54:05Z fnussber $
//
package jsky.app.ot.tpe;

import edu.gemini.shared.util.immutable.None;
import edu.gemini.shared.util.immutable.Option;
import edu.gemini.shared.util.immutable.Some;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

/**
 * This is a helper object used to manage the various TpeImageFeatures.
 * This logically belongs with the TelescopePosEditor code but has been
 * moved out to simplify.
 */
final class TpeFeatureManager {

    /**
     * Local class used to map image features to toggle button widgets
     */
    private static class TpeFeatureData {
        final TpeImageFeature feature;  // The image feature itself
        final JToggleButton button;     // The button used to toggle it
        final Option<Component> key;    // Key used to explain the feature

        public TpeFeatureData(TpeImageFeature feature, JToggleButton button, Option<Component> key) {
            this.feature = feature;
            this.button = button;
            this.key = key;
        }
    }

    /**
     * Reference to the position editor toolbar
     */
    private final TpeToolBar _tpeToolBar;

    /**
     * Reference to the image display widget
     */
    private final TpeImageWidget _iw;

    /**
     * Maps image features to toggle button widgets
     */
    private final Map<String, TpeFeatureData> _featureMap = new Hashtable<String, TpeFeatureData>();


    /**
     * Constructor
     */
    TpeFeatureManager(TelescopePosEditor tpe, TpeImageWidget iw) {
        _tpeToolBar = tpe.getTpeToolBar();
        _iw = iw;
        _tpeToolBar.hideViewButtons();
    }

    /**
     * Add a feature.
     */
    public void addFeature(final TpeImageFeature tif) {
        String name = tif.getName();

        // See if this feature is already present.
        if (_featureMap.containsKey(name)) return;

        JToggleButton btn = new JCheckBox(name);
        btn.setToolTipText(tif.getDescription());
        btn.setVisible(false);
        btn.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    _iw.addFeature(tif);
                } else {
                    _iw.deleteFeature(tif);
                }
            }
        });

        Option<Component> keyPanel = None.instance();
        if (!tif.getKey().isEmpty()) {
            keyPanel = new Some<Component>(TpeToolBar.createKeyPanel(tif.getKey().getValue()));
        }
        _featureMap.put(name, new TpeFeatureData(tif, btn, keyPanel));

        _tpeToolBar.addViewItem(btn, tif.getCategory());
        if (!keyPanel.isEmpty())
            _tpeToolBar.addViewItem(keyPanel.getValue(), tif.getCategory());

        if (tif.isEnabledByDefault()) btn.setSelected(true);
    }

    public void updateAvailableOptions(Collection<TpeImageFeature> feats, TpeContext ctx) {

        // Remove all the existing options.
        for (TpeFeatureData data : _featureMap.values()) {
            data.button.setVisible(false);
            if (!data.key.isEmpty()) {
                data.key.getValue().setVisible(false);
            }
        }

        // Add view options according to the enabled state of each item.
        for (TpeImageFeature feature : feats) {
            if (feature.isEnabled(ctx)) {
                setVisible(feature, true);
            }
        }

        // Update the guider selector at the bottom of the tool bar.
        _tpeToolBar.getGuiderSelector().init(ctx.obsShellOrNull());
    }

    /**
     * Get the named feature.
     */
    public TpeImageFeature getFeature(String name) {
        TpeFeatureData tfd = _featureMap.get(name);
        if (tfd == null) return null;
        return tfd.feature;
    }

    /**
     * Is the given feature already added?
     */
    public boolean isFeaturePresent(TpeImageFeature tif) {
        return _featureMap.containsKey(tif.getName());
    }

    /**
     */
    public void setSelected(TpeImageFeature tif, boolean selected) {
        TpeFeatureData tfd = _featureMap.get(tif.getName());
        if (selected != tfd.button.isSelected()) {
            tfd.button.doClick();
        }
    }

    /**
     */
    public void setVisible(TpeImageFeature tif, boolean visible) {
        TpeFeatureData tfd = _featureMap.get(tif.getName());
        if (visible != tfd.button.isVisible()) {
            tfd.button.setVisible(visible);
            if (!tfd.key.isEmpty()) {
                tfd.key.getValue().setVisible(visible);
            }
        }
    }
}
