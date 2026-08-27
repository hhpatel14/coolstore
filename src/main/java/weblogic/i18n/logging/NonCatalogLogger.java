package weblogic.i18n.logging;

import java.util.logging.Logger;

/**
 * Legacy WebLogic compatibility stub - replaced with standard Java logging
 */
@Deprecated
public class NonCatalogLogger {
    
    private Logger logger;

    public NonCatalogLogger() {
        this.logger = Logger.getLogger(NonCatalogLogger.class.getName());
    }

    public NonCatalogLogger(String logName) {
        this.logger = Logger.getLogger(logName);
    }

    public void info(String msg) {
        logger.info(msg);
    }
}
