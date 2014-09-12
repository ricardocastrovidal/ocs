// Copyright 2003
// Association for Universities for Research in Astronomy, Inc.,
// Observatory Control System, Gemini Telescopes Project.
//
// $Id: ElevationPlotInternalFrame.java 4731 2004-05-17 20:46:49Z brighton $

package jsky.plot;

import java.awt.BorderLayout;

import javax.swing.JInternalFrame;

import jsky.util.Preferences;


/**
 * Provides a top level window for an ElevationPlotPanel panel.
 *
 * @version $Revision: 4731 $
 * @author Allan Brighton
 */
public class ElevationPlotInternalFrame extends JInternalFrame {

    // The GUI panel
    private ElevationPlotPanel _plotPanel;


    /**
     * Create a top level window containing an ElevationPlotPanel.
     */
    public ElevationPlotInternalFrame() {
        super("Elevation Plot", true, true, true, true);
        _plotPanel = new ElevationPlotPanel(this);
        setJMenuBar(new ElevationPlotMenuBar(_plotPanel));
        getContentPane().add(_plotPanel, BorderLayout.CENTER);
        pack();
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        Preferences.manageLocation(this);
        setVisible(true);
    }

    public ElevationPlotPanel getPlotPanel() {
        return _plotPanel;
    }
}

