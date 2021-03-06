/*
 * Copyright 2003 Association for Universities for Research in Astronomy, Inc.,
 * Observatory Control System, Gemini Telescopes Project.
 *
 * $Id: TargetDesc.java 42349 2012-03-01 13:03:51Z swalker $
 */

package jsky.coords;


/**
 * A simple class describing a target object and some related information.
 *
 * @version $Revision: 42349 $
 * @author Allan Brighton
 */
public class TargetDesc {
    public static enum ElConstraintType {
    	NONE, HOUR_ANGLE, AIRMASS;
        public static ElConstraintType getElConstraintType(String name) {
        	try {
        		return valueOf(name);
        	} catch (IllegalArgumentException iae) {
        		return NONE;
        	}
        }
    }

    private String _name;
    private WorldCoords _coords;
    private String _description;
    private String _priority;
    private String _category;
    private ElConstraintType _elType;
    private double _elMin;
    private double _elMax;


    public TargetDesc(String name, WorldCoords coords) {
        _name = name;
        _coords = coords;
        _description = name;
        _priority = "";
        _category = "";
    }

    public TargetDesc(String name, WorldCoords coords, String description, String priority, String category) {
        _name = name;
        _coords = coords;
        _description = description;
        _priority = priority;
        _category = category;
    }

    public TargetDesc(String name, WorldCoords coords, String description, String priority, String category,
                          ElConstraintType elType, Double elMin, Double elMax) {
        _name = name;
        _coords = coords;
        _description = description;
        _priority = priority;
        _category = category;
        _elType = elType;
        _elMin = elMin;
        _elMax = elMax;
    }

    public String getName() {
        return _name;
    }

    public String toString() {
        return _name;
    }

    public WorldCoords getCoordinates() {
        return _coords;
    }

    public String getDescription() {
        return _description;
    }

    public String getPriority() {
        return _priority;
    }

    public String getCategory() {
        return _category;
    }

    /** Return an array of one or more Strings describing the target */
    public String[] getDescriptionFields() {
        return new String[]{_name};
    }

    public double getElMax() {
        return _elMax;
    }

    public double getElMin() {
        return _elMin;
    }

    public ElConstraintType getElType() {
        return _elType;
    }
}

