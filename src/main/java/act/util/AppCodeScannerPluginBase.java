package act.util;

import act.Act;
import act.ActComponent;
import act.app.App;
import act.app.AppByteCodeScanner;
import act.app.AppSourceCodeScanner;
import act.plugin.Plugin;
import org.osgl.logging.L;
import org.osgl.logging.Logger;

@ActComponent
public abstract class AppCodeScannerPluginBase extends DestroyableBase implements Plugin {

    protected final static Logger logger = L.get(AppCodeScannerPluginBase.class);

    @Override
    public void register() {
        if (!load()) {
            logger.info("Scanner plugin cannot be loaded: " + getClass().getName());
            return;
        }
        logger.debug("Registering %s", getClass().getName());
        Act.scannerPluginManager().register(this);
    }

    public abstract AppSourceCodeScanner createAppSourceCodeScanner(App app);

    public abstract AppByteCodeScanner createAppByteCodeScanner(App app);

    public abstract boolean load();
}
