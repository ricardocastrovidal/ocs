//
// $
//

package jsky.app.ot.gemini.editor.targetComponent;

import edu.gemini.shared.util.immutable.*;
import edu.gemini.spModel.target.SPTarget;
import edu.gemini.spModel.target.TelescopePosWatcher;
import edu.gemini.spModel.target.WatchablePos;

import javax.swing.*;
import javax.swing.text.DefaultFormatter;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.text.NumberFormat;

/**
 * An editor for proper motion.
 */
final class ProperMotionEditor implements TelescopePosEditor {
    private final JPanel pan;

    private final JFormattedTextField pmRa;
    private final JFormattedTextField pmDec;

    private SPTarget target;

    ProperMotionEditor() {
        pan = new JPanel(new GridBagLayout()) {{
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        }};

        // Create two text fields, one for PM in RA and one for PM in dec
        pmRa  = createTextField("Proper motion in RA");
        pmDec = createTextField("Proper motion in declination");

        // A list of tuples of label and widget
        ImList<Pair<String,JFormattedTextField>> items =
                DefaultImList.create(
                        new Pair<String,JFormattedTextField>("RA", pmRa),
                        new Pair<String,JFormattedTextField>("Dec", pmDec)
                );

        // Place the items in the panel
        items.zipWithIndex().foreach(new ApplyOp<Tuple2<Pair<String,JFormattedTextField>, Integer>>() {
            @Override public void apply(Tuple2<Pair<String,JFormattedTextField>, Integer> tup) {
                // Index -- the gridy value.
                final int y = tup._2();

                // Gap to leave below widgets -- only for the first row.
                final int vpad = (y==0) ? 5 : 0;

                // Label
                pan.add(new JLabel(tup._1()._1()), new GridBagConstraints(){{
                    gridx=0; gridy=y; anchor=EAST; insets=new Insets(0, 0, vpad, 5);
                }});
                // Text Field widget
                pan.add(tup._1()._2(), new GridBagConstraints(){{
                    gridx=1; gridy=y; anchor=WEST; insets=new Insets(0, 0, vpad, 5);
                }});
                // Units label
                pan.add(new JLabel("mas/year"), new GridBagConstraints(){{
                    gridx=2; gridy=y; anchor=WEST; insets=new Insets(0, 0, vpad, 0);
                }});
            }
        });
    }

    private static JFormattedTextField createTextField(final String tip) {
        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMinimumFractionDigits(1);
        nf.setMinimumIntegerDigits(1);
        nf.setGroupingUsed(false);

        return new JFormattedTextField(nf) {{
            setColumns(5);
            ((DefaultFormatter) getFormatter()).setCommitsOnValidEdit(true);
            setToolTipText(tip);
            setMinimumSize(getPreferredSize());
        }};
    }

    public Component getComponent() {
        return pan;
    }

    private final TelescopePosWatcher watcher = new TelescopePosWatcher() {
        @Override public void telescopePosLocationUpdate(WatchablePos tp) { }
        @Override public void telescopePosGenericUpdate(WatchablePos tp) {
            reinit();
        }
    };

    private final PropertyChangeListener updatePmRaListener = new PropertyChangeListener() {
        @Override public void propertyChange(PropertyChangeEvent evt) {
            try {
                Number d = (Number) evt.getNewValue();
                target.deleteWatcher(watcher);
                target.setPropMotionRA(d == null ? "0.0" : String.valueOf(d));
                target.addWatcher(watcher);
            } catch (Exception ex) {
                // do nothing
            }
        }
    };

    private final PropertyChangeListener updatePmDecListener = new PropertyChangeListener() {
        @Override public void propertyChange(PropertyChangeEvent evt) {
            try {
                Number d = (Number) evt.getNewValue();
                target.deleteWatcher(watcher);
                target.setPropMotionDec(d == null ? "0.0" : String.valueOf(d));
                target.addWatcher(watcher);
            } catch (Exception ex) {
                // do nothing
            }
        }
    };

    public void edit(final SPTarget target) {
        if (this.target == target) return;
        if (this.target != null) this.target.deleteWatcher(watcher);
        if (target != null) target.addWatcher(watcher);

        this.target = target;
        reinit();
    }

    private void reinit() {
        pmRa.removePropertyChangeListener("value", updatePmRaListener);
        pmRa.setText(target == null ? "0.0" : target.getPropMotionRA());
        pmRa.addPropertyChangeListener("value", updatePmRaListener);

        pmDec.removePropertyChangeListener("value", updatePmDecListener);
        pmDec.setText(target == null ? "0.0" : target.getPropMotionDec());
        pmDec.addPropertyChangeListener("value", updatePmDecListener);
    }
}