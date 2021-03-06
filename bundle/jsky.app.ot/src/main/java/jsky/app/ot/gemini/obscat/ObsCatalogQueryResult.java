// Copyright 2001 Association for Universities for Research in Astronomy, Inc.,
// Observatory Control System, Gemini Telescopes Project.
//
// $Id: ObsCatalogQueryResult.java 6821 2005-12-07 12:15:24Z brighton $
//
package jsky.app.ot.gemini.obscat;

import edu.gemini.spModel.core.SPProgramID;
import jsky.app.ot.shared.gemini.obscat.ObsCatalogInfo;
import jsky.catalog.FieldDescAdapter;
import jsky.catalog.gui.CatalogUIHandler;
import jsky.catalog.gui.QueryResultDisplay;
import jsky.catalog.skycat.SkycatConfigEntry;
import jsky.catalog.skycat.SkycatConfigFile;
import jsky.catalog.skycat.SkycatTable;
import jsky.util.gui.DialogUtil;

import javax.swing.JComponent;
import java.util.List;
import java.util.Properties;
import java.util.Vector;


/**
 * Holds the result of a query to an ObsCatalog.
 *
 * @author Allan Brighton
 */
public class ObsCatalogQueryResult extends SkycatTable implements CatalogUIHandler {

    // A vector of (progId, obsId, groupName) rows, corresponding to the data rows
    private final Vector _idRows;

    private final List<DB> _dbs;

    /** The index of the Gemini program id in a vector row */
    public static final int PROG_ID = 0;

    /** The index of the observation id in a vector row */
    public static final int OBS_ID = 1;

    /** The index of the observation group name in a vector row */
    public static final int GROUP = 2;

    // Cached reference to the widget displaying the query results
    private static ObsCatalogQueryResultDisplay _queryResultDisplay;

    /**
     * Construct a new ObsCatalogQueryResult with the given data.
     *
     * @param configEntry a config entry describing the table
     * @param dataRows a vector of data rows, each of which is a vector of column values.
     * @param idRows a vector of (progId, obsId) rows, corresponding to the data rows
     * @param fieldDescr an array of objects describing the table columns
     */
    public ObsCatalogQueryResult(SkycatConfigEntry configEntry, Vector dataRows, Vector idRows, List<DB> dbs,
                                 FieldDescAdapter[] fieldDescr) {
        super(configEntry, dataRows, fieldDescr);
        _idRows = idRows;
        _dbs = dbs;

        final Properties props = getProperties();
        props.setProperty(SkycatConfigFile.RA_COL, String.valueOf(ObsCatalogInfo.RA_COL));
        props.setProperty(SkycatConfigFile.DEC_COL, String.valueOf(ObsCatalogInfo.DEC_COL));

    }

    /** Return a vector of (progId, obsId) rows, corresponding to the data rows */
    public Vector getIdRows() {
        return _idRows;
    }

    public DB getDatabase(int row) {
        try {
            return _dbs.get(row);
        } catch (IndexOutOfBoundsException ioobe) {
            return null;
        }
    }

    /** Implement the {@link CatalogUIHandler} interface to get a custom GUI */
    public JComponent makeComponent(QueryResultDisplay display) {
        if (_queryResultDisplay == null)
            _queryResultDisplay = new ObsCatalogQueryResultDisplay(this, display);
        else
            _queryResultDisplay.setQueryResult(this);
        return _queryResultDisplay;
    }

    // Redefined from base class to update _idRows or disable
    public void removeRow(int row) {
        super.removeRow(row);
        if (_idRows != null && _idRows.size() > row) {
            _idRows.remove(row);
        }
    }
    public void addRow(Vector rowData) {
        DialogUtil.error("Can't add rows to this table: Not implemented.");
    }
    public void setReadOnly(boolean b) {
        if (!b) {
            DialogUtil.error("Can't edit rows in this table: Not implemented.");
            return;
        }
        super.setReadOnly(b);
    }
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    public void updateDatabase(SPProgramID pid, DB db) {
        for (int i = 0; i < getRowCount(); i++)
            if (pid.equals(getValueAt(i, ObsCatalogInfo.PROG_REF)))
                _dbs.set(i, db);
    }

}
