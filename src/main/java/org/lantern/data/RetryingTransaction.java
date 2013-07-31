package org.lantern.data;

import java.util.ConcurrentModificationException;
import java.util.logging.Logger;

import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;

public abstract class RetryingTransaction<T> {
    private final transient Logger log = Logger.getLogger(getClass().getName());
    Boolean failed = null;

    public boolean failed() {
        if (failed == null) {
            throw new RuntimeException("Run transaction before checking for failure");
        }
        return failed;
    }
    public T run() {
        for (int retries=Dao.TXN_RETRIES; retries>0; retries--) {
            final Objectify ofy = ObjectifyService.beginTransaction();
            try {
                T result = run(ofy);
                failed = false;
                return result;
            } catch (final ConcurrentModificationException e) {
                log.info("Concurrent modification! Retrying...");
                continue;
            } finally {
                if (ofy.getTxn().isActive()) {
                    ofy.getTxn().rollback();
                }
            }
        }
        failed = true;
        return null;
    }

    protected abstract T run(Objectify ofy);
}