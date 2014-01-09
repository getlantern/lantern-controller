package org.lantern.endpoints;

import java.util.logging.Logger;

import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.users.User;

/**
 * Base class for Endpoints for interacting with the friends of a given user.
 */
public class BaseFriendEndpoint {

    private final transient Logger log = Logger.getLogger(getClass().getName());

    protected void checkAuthorization(final User user)
            throws UnauthorizedException {
        if (user == null) {
            log.warning("User is unauthorized!");
            throw new UnauthorizedException("Unauthorized");
        }
    }

}