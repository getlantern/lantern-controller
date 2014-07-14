package org.lantern.endpoints;

import java.util.logging.Logger;

import org.lantern.loggly.LoggerFactory;

import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.users.User;

/**
 * Base class for Endpoints.
 */
public class BaseEndpoint {

    protected final transient Logger log = LoggerFactory.getLogger(getClass());

    protected void checkAuthorization(final User user)
            throws UnauthorizedException {
        if (user == null) {
            log.warning("User is unauthorized!");
            throw new UnauthorizedException("Unauthorized");
        }
    }

}