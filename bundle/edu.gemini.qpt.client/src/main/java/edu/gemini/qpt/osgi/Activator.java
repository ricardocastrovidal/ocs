package edu.gemini.qpt.osgi;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.logging.Logger;

import edu.gemini.qpt.core.util.LttsServicesClient;

import edu.gemini.qpt.ui.action.PublishAction;
import edu.gemini.spModel.core.Version;
import edu.gemini.util.security.auth.keychain.KeyChain;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import edu.gemini.qpt.ui.ShellAdvisor;
import edu.gemini.qpt.ui.util.Platform;
import edu.gemini.ui.workspace.IShellAdvisor;

import javax.swing.*;

/**
 * BundleActivator for the QPT application.
 * @author rnorris
 */
public final class Activator implements BundleActivator, AWTEventListener {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = Logger.getLogger(Activator.class.getName());
	private ShellAdvisor advisor;
	private BundleContext context;

    // Credentials for publishing
    private static final String PROP_INTERNAL_USER = "edu.gemini.qpt.ui.action.destination.internal.user";
    private static final String PROP_INTERNAL_PASS = "edu.gemini.qpt.ui.action.destination.internal.pass";
    private static final String PROP_PACHON_USER   = "edu.gemini.qpt.ui.action.destination.pachon.user";
    private static final String PROP_PACHON_PASS   = "edu.gemini.qpt.ui.action.destination.pachon.pass";

	@SuppressWarnings({ "deprecation", "unchecked" })
	public void start(final BundleContext context) throws Exception {

        LttsServicesClient.LTTS_SERVICES_NORTH_URL = context.getProperty("edu.gemini.qpt.ltts.services.north.url");
        if (LttsServicesClient.LTTS_SERVICES_NORTH_URL == null) {
            LOGGER.warning("Missing bundle.properties value edu.gemini.qpt.ltts.services.north.url");
        }
        LttsServicesClient.LTTS_SERVICES_SOUTH_URL = context.getProperty("edu.gemini.qpt.ltts.services.south.url");
        if (LttsServicesClient.LTTS_SERVICES_SOUTH_URL == null) {
            LOGGER.warning("Missing bundle.properties value edu.gemini.qpt.ltts.services.south.url");
        }

		this.context = context;

		Dictionary<String, String> headers = context.getBundle().getHeaders();

        // TODO: this is set to the application install dir where we can find
        // the help files
        final String root = new File(System.getProperty("user.dir")).toURI().toString();

        // Ok we're just going to grab this. It better be there.
        final ServiceReference<KeyChain> acRef = context.getServiceReference(KeyChain.class);
        final KeyChain ac = context.getService(acRef);

        final PublishAction.Destination internal, pachon;

        internal = new PublishAction.Destination(
            "gnconfig.gemini.edu",
            getProp(PROP_INTERNAL_USER),
            getProp(PROP_INTERNAL_PASS),
            "/gemsoft/var/data/qpt",
            "http://internal.gemini.edu/science/");

        pachon = new PublishAction.Destination(
            "gsconfig.gemini.edu",
            getProp(PROP_PACHON_USER),
            getProp(PROP_PACHON_PASS),
            "/gemsoft/var/data/qpt",
            null);

        this.advisor = new ShellAdvisor(headers.get("Bundle-Name"), Version.current.toString(), root, ac, internal, pachon);
		context.registerService(IShellAdvisor.class.getName(), advisor,  new Hashtable());

		if (Boolean.getBoolean("ctrl.key.hack")) {
			LOGGER.info("Enabling ctrl key hack.");
			Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.KEY_EVENT_MASK);
		}

	}

    private String getProp(String key) {
        String val = context.getProperty(key);
        if (val == null)
            throw new RuntimeException("Configuration key " + key + " was not specified.");
        return val;
    }

	public void stop(BundleContext context) throws Exception {
		Toolkit.getDefaultToolkit().removeAWTEventListener(this);
		this.advisor = null;
		this.context = null;
	}

	public void modifiedService(ServiceReference arg0, Object arg1) {
		// nop
	}

	public void eventDispatched(AWTEvent event) {
		// TODO Auto-generated method stub

		KeyEvent ke = (KeyEvent) event;
		switch (ke.getKeyCode()) {
		case KeyEvent.VK_RIGHT:
		case KeyEvent.VK_LEFT:

			if ((ke.getModifiers() & Platform.MENU_ACTION_MASK) == 0) {
				ke.consume();
				KeyEvent ke2 = new KeyEvent((Component) ke.getSource(), ke.getID(), ke.getWhen(),
						ke.getModifiers() | Platform.MENU_ACTION_MASK, ke.getKeyCode(), ke.getKeyChar());
				Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(ke2);

			}
		}

	}

}
