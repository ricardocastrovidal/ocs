// Copyright 1997 Association for Universities for Research in Astronomy, Inc.,
// Observatory Control System, Gemini Telescopes Project.
// See the file COPYRIGHT for complete details.
//
// $Id: TpeDraggableFeature.java 21627 2009-08-21 18:30:58Z swalker $
//
package jsky.app.ot.tpe;

import edu.gemini.shared.util.immutable.Option;


/**
 * This interface should be supported by TpeImageFeatures that are
 * "draggable".
 */
public interface TpeDraggableFeature {
    /**
     * Start dragging the object.
     */
    public Option<Object> dragStart(TpeMouseEvent evt, TpeImageInfo tii);

    /**
     * Drag to a new location.
     */
    public void drag(TpeMouseEvent evt);

    /**
     * Stop dragging.
     */
    public void dragStop(TpeMouseEvent evt);

    /**
     * Return true if the mouse is over an active part of this image feature
     * (so that dragging can begin there).
     */
    public boolean isMouseOver(TpeMouseEvent evt);
}

