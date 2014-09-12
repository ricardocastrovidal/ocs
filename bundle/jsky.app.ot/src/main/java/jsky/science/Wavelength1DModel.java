//=== File Prolog =============================================================
//	This code was adapted by NASA, Goddard Space Flight Center, Code 588
//	for the Scientist's Expert Assistant (SEA) project.
//
//--- Development History -----------------------------------------------------
//
//	06/15/00	S. Grosvenor / 588 Booz-Allen
//		Original implementation
//
//--- DISCLAIMER---------------------------------------------------------------
//
//	This software is provided "as is" without any warranty of any kind, either
//	express, implied, or statutory, including, but not limited to, any
//	warranty that the software will conform to specification, any implied
//	warranties of merchantability, fitness for a particular purpose, and
//	freedom from infringement, and any warranty that the documentation will
//	conform to the program, or any warranty that the software will be error
//	free.
//
//	In no event shall NASA be liable for any damages, including, but not
//	limited to direct, indirect, special or consequential damages, arising out
//	of, resulting from, or in any way connected with this software, whether or
//	not based upon warranty, contract, tort or otherwise, whether or not
//	injury was sustained by persons or property or otherwise, and whether or
//	not loss was sustained from or arose out of the results of, or use of,
//	their software or services provided hereunder.
//=== End File Prolog====================================================================

package jsky.science;

import jsky.science.util.ReplaceablePropertyChangeListener;

/**
 * This interface provides a top-level interface for lists that are indexed by wavelengths.
 * Works with the Wavlength class in the Quantity hierarchy to provide structure
 * for containing, querying, and access information such as detector throughputs,
 * spectra, and other data where the datapoints are organized by wavelengths
 *
 * <P>This code was developed by NASA, Goddard Space Flight Center, Code 588
 *    for the Scientist's Expert Assistant (SEA) project for Next Generation
 *    Space Telescope (NGST).
 *
 * @version 11.27.2000
 * @author 	Sandy Grosvenor
 *
 */
public interface Wavelength1DModel extends ScienceObjectNodeModel {

    /**
     * Return the value of the model at the specified Wavelength.
     */
    double getValue(Wavelength inWL);

    /**
     * Return the area "under the curve" of the model from the specified
     * minimum to maximum Wavelengths.
     */
    double getArea(Wavelength minWL, Wavelength maxWL);

    /**
     * Return the area "under the curve" of the model from the specified
     * minimum to maximum Wavelengths.
     */
    double getArea(Wavelength minWL, Wavelength maxWL, boolean interpolate);

    /**
     * Return the total area "under the curve" for all wavelengths
     */
    double getArea();

    /**
     * Return the total area "under the curve" for all wavelengths
     */
    double getArea(boolean interpolate);

    /**
     * Return true if model is editable after instantiation, false otherwise
     */
    boolean isEditable();

    /**
     * Sets the data value for specified wavelength. (Should be ignored in
     *  implementations where isEditable() is false
     */
    void setValue(Wavelength inWl, double newVal);

    /**
     * Register to be notified of change events on this model.
     */
    void addPropertyChangeListener(ReplaceablePropertyChangeListener listener);

    /**
     * Un-register to be notified of change events on this model.
     */
    void removePropertyChangeListener(ReplaceablePropertyChangeListener listener);

    /**
     * Return an array of doubles containing the data in the model.
     * This data points in the returned array should match the wavelength data
     * generated by toArrayWavelength.
     * If either wavelength is null, the model should provide a default minimum
     * or maximum.  Similarly, if the nPts is 0, then the model should provide
     * a default number of points.  These defaults should be consistent between
     * the toArrayData() method and toArrayWavelengths()
     */
    double[] toArrayData(Wavelength minWL, Wavelength maxWL, int nPts);

    /**
     * Return an array of doubles containing values from the model
     * at the specfied array of wavelengths values.  The units of the wavelength values
     * should specified in the current default units of the Wavelength class
     */
    double[] toArrayData(double[] wavelengths);

    /**
     * Return an array of doubles containing a list of wavelengths points in the model.
     * The units of the return values should be the current default units of
     * the Wavelength class.
     * If either wavelength is null, the model should provide a default minimum
     * or maximum.  Similarly, if the nPts is 0, then the model should provide
     * a default number of points.  These defaults should be consistent between
     * the toArrayData( minWl, maxWl, nPts) method
     */
    double[] toArrayWavelengths(Wavelength minWL, Wavelength maxWL, int nPts);

    /**
     * Return an array of doubles containing a list of wavelengths points in the model.
     * The units of the return values should be the current default units of
     * the Wavelength class.
     * If either wavelength is null, the model should provide a default minimum
     * or maximum.  Similarly, if the nPts is 0, then the model should provide
     * a default number of points.  These defaults should be consistent between
     * the toArrayData( minWl, maxWl, nPts) method
     */
    double[] toArrayWavelengths(Wavelength minWL, Wavelength maxWL, int nPts, String units);

    /**
     * Returns the number of data points in the model.  If the
     * subclass contains a formula instead of a list, this method should return a
     * default number of point compatible with calls to toArrayData and toArrayWavelengths
     */
    int getNumPoints();

    /**
     * returns the units string for the data values in the model. May be null
     */
    public String getFluxUnits();

    /**
     * sets the units string for the data values in the model, may be null
     */
    public void setFluxUnits(String newUnits) throws UnitsNotSupportedException;

}
