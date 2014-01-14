package org.lantern.loggly;

import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 * Factory for Loggers that also log to Loggly.
 */
public class LoggerFactory {
    private static final LogglyHandler HANDLER = new LogglyHandler();

    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    public static Logger getLogger(String name) {
        return addLogglyHandlerIfNecessary(Logger.getLogger(name));

    }

    synchronized private static Logger addLogglyHandlerIfNecessary(Logger logger) {
        for (Handler handler : logger.getHandlers()) {
            if (handler == HANDLER) {
                return logger;
            }
        }
        logger.addHandler(HANDLER);
        return logger;
    }
}
