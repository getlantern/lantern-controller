package org.lantern.loggly;

import java.util.Date;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.lantern.LanternControllerConstants;

/**
 * LogglyHandler logs SEVERE messages to Loggly.
 */
public class LogglyHandler extends Handler {

    private Loggly loggly = new Loggly(
            LanternControllerConstants.IS_RUNNING_IN_SANDBOX);

    /*
     * (non-API documentation)
     * 
     * @see java.util.logging.Handler#publish(java.util.logging.LogRecord)
     */
    public void publish(LogRecord record) {
        // ensure that this log record should be logged by this Handler
        if (!isLoggable(record))
            return;

        LogglyMessage msg = new LogglyMessage(
                String.format("%1$s.appspot.com",
                              LanternControllerConstants.CONTROLLER_ID),
                record.getMessage(),
                new Date(record.getMillis())).sanitized();
        Throwable thrown = record.getThrown();
        if (thrown != null) {
            msg.setThrowable(thrown);
        }
        loggly.log(msg);
    }

    @Override
    public boolean isLoggable(LogRecord record) {
        return super.isLoggable(record) && record.getLevel() == Level.SEVERE;
    }

    @Override
    public void flush() {
        // do nothing
    }

    @Override
    public void close() throws SecurityException {
        // do nothing
    }
}
