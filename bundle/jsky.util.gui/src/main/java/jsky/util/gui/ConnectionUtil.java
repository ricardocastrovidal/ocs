/*
 * Copyright 2000 Association for Universities for Research in Astronomy, Inc.,
 * Observatory Control System, Gemini Telescopes Project.
 *
 * $Id: ConnectionUtil.java 4414 2004-02-03 16:21:36Z brighton $
 */

package jsky.util.gui;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * A utility class for getting a URL connection in a background thread
 * without hanging.  Used to solve the problem of a background thread
 * hanging (even after calling Thread.interrupt()) while waiting for
 * URL.openConnection() to return.
 *
 * @author Allan Brighton
 * @version $Revision: 4414 $
 */
public class ConnectionUtil {

    /**
     * URL to connect to
     */
    private URL url;

    /**
     * The URL connection object.
     */
    private URLConnection connection;

    /**
     * Set to true if the background thread is interrupted
     */
    private boolean interrupted = false;

    /**
     * Exception thrown while trying to open the connection
     */
    private Exception exception;

    /**
     * Background thread making the connection.
     */
    private SwingWorker worker;


    /**
     * Initialize with the given URL
     */
    public ConnectionUtil(URL url) {
        this.url = url;
    }

    public URLConnection openConnection() throws IOException {
        // run in a separate thread, so the user can monitor progress and cancel it, if needed
        worker = new SwingWorker() {

            public Object construct() {
                try {
                    URLConnection connection = url.openConnection();
                    connection.getContentLength(); // forces the actual read...
                    return connection;
                } catch (Exception e) {
                    return e;
                }
            }

            public void finished() {
                Object o = getValue();
                if (o instanceof URLConnection)
                    connection = (URLConnection) o;
                else if (o instanceof Exception)
                    exception = (Exception) o;
            }
        };
        worker.start();

        interrupted = false;

        // wait for the connection, or an interruption, or an exception
        while (connection == null && exception == null && !interrupted) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                interrupted = true;
                break;
            }
            interrupted = Thread.interrupted();
        }

        if (interrupted) {
            worker.interrupt();
            return null;
        }
        if (exception != null) {
            if (exception instanceof IOException)
                throw (IOException) exception;
            else
                throw new RuntimeException(exception.toString());
        }

        return connection;
    }


    /**
     * Interrupt the connection
     */
    public void interrupt() {
        interrupted = true;
        worker.interrupt();
    }
}
