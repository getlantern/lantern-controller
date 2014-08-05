package org.lantern.endpoints;

import java.util.logging.Logger;

import org.lantern.EmailAddressUtils;
import org.lantern.data.Dao;
import org.lantern.data.LanternUser;
import org.lantern.loggly.LoggerFactory;

import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.users.User;

/**
 * Utility class for Endpoints.
 */
public final class Endpoints {

    private static final transient Logger log = 
            LoggerFactory.getLogger(Endpoints.class);
    
    public static LanternUser checkAuthorizationAndCreateUser(final User user)
            throws UnauthorizedException {
        if (user == null) {
            log.warning("User is unauthorized!");
            throw new UnauthorizedException("Unauthorized");
        }
        
        final Dao dao = new Dao();
        final String from = EmailAddressUtils.normalizedEmail(user.getEmail());
        final LanternUser lu = dao.createOrUpdateUser(from, null, null, null);
        dao.updateLastAccessed(from);
        
        return lu;
    }
}