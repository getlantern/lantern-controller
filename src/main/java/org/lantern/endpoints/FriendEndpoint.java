package org.lantern.endpoints;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Named;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import org.lantern.data.PMF;
import org.lantern.data.ServerFriend;
import org.lantern.state.Friend;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.users.User;

/**
 * Endpoint for interacting with the friends of a given user.
 */
@Api(name = "friend", 
     version = "v1", 
     clientIds = { "323232879315-bea7ng41i8fsvua1takpcprbpd38nal9.apps.googleusercontent.com" } ,
     scopes = { "https://www.googleapis.com/auth/userinfo.email" })
public class FriendEndpoint {

    private final transient Logger log = Logger.getLogger(getClass().getName());
    
    /**
     * This method lists all the entities inserted in datastore. It uses HTTP
     * GET method.
     * 
     * @return List of all entities persisted.
     * @throws UnauthorizedException If the user is unauthorized.
     */
    @ApiMethod(
            httpMethod = "GET",
            name = "friend.list", 
            path = "friend/list")
    public List<Friend> listFriend(final com.google.appengine.api.users.User user) 
            throws UnauthorizedException {
        checkAuthorization(user);
        
        log.info("Listing friends");
        final String email = email(user);
        final PersistenceManager mgr = getPersistenceManager();
        final List<Friend> result = new ArrayList<Friend>();
        try {
            final Query query = mgr.newQuery(ServerFriend.class);
            query.setFilter("userEmail == '"+email+"'");
            
            // Make sure we stay under the app engine limit.
            query.setRange(0L, 999L);
            for (final Object obj : (List<Object>) query.execute()) {
                result.add(((ServerFriend) obj));
            }
        } finally {
            mgr.close();
        }
        return result;
    }

    /**
     * This method gets the entity having primary key id. It uses HTTP GET
     * method.
     * 
     * @param id The primary key of the java bean.
     * @return The entity with primary key id.
     * @throws UnauthorizedException If the user is unauthorized.
     */
    @ApiMethod(
            httpMethod = "GET", 
            name = "friend.get",
            path = "friend/get/{id}")
    public Friend getFriend( @Named("id") Long id, 
            final com.google.appengine.api.users.User user) 
                    throws UnauthorizedException {
        checkAuthorization(user);
        PersistenceManager mgr = getPersistenceManager();
        ServerFriend friend = null;
        try {
            friend = mgr.getObjectById(ServerFriend.class, id);
            if (!friend.getUserEmail().toLowerCase().equals(email(user))) {
                log.warning("Emails don't match?");
                throw new UnauthorizedException("Unauthorized");
            }
        } finally {
            mgr.close();
        }
        return friend;
    }

    /**
     * This inserts the entity into App Engine datastore. It uses HTTP POST/
     * 
     * @param task The entity to be inserted.
     * @return The inserted entity.
     * @throws UnauthorizedException If the user is unauthorized.
     */
    @ApiMethod(
            httpMethod = "POST",
            name = "friend.insert",
            path = "friend/insert")
    public Friend insertFriend(final ServerFriend friend,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        checkAuthorization(user);
        String userEmail = email(user);
        log.info(userEmail + " is considering inserting friend " + friend.getEmail());
        friend.setUserEmail(userEmail);
        final PersistenceManager mgr = getPersistenceManager();
        final ServerFriend existing = getExistingFriend(friend, user);
        if (existing != null) {
            log.warning("Found existing friend " + existing.getEmail());
            return existing;
        }
        
        log.info("Inserting friend");
        persist(mgr, friend);
        return friend;
    }

    /**
     * This method is used for updating an entity. It uses the HTTP PUT method.
     * 
     * @param friend The entity to be updated.
     * @return The updated entity.
     * @throws UnauthorizedException If the user is unauthorized.
     */
    @ApiMethod(
            httpMethod = "POST",
            name = "friend.update",
            path = "friend/update")
    public Friend updateFriend(final ServerFriend friend, 
        final com.google.appengine.api.users.User user) 
                throws UnauthorizedException {
        checkAuthorization(user);
        log.info("Updating friend...");
        final PersistenceManager mgr = getPersistenceManager();
        final ServerFriend existing = getExistingFriend(friend, user);
        if (existing != null) {
            log.warning("Found existing friend?");
            update(existing, friend);
            persist(mgr, existing);
            return existing;
        }
        friend.setUserEmail(email(user));
        if (friend.getId() == null) {
            log.warning("No ID on friend? Ignoring update.");
            return friend;
        }
        persist(mgr, friend);
        return friend;
    }
    

    private void update(final ServerFriend older, final ServerFriend newer) {
        older.setName(newer.getName());
        older.setStatus(newer.getStatus());
        older.setLastUpdated(newer.getLastUpdated());
    }

    /**
     * Check for an existing friend to avoid duplicate friends that somehow
     * are creeping into the database.
     * 
     * @param friend The friend of the logged-in user.
     * @param user The user with the given friend.
     * @return The existing friend or <code>null</code> if no such friend
     * exists.
     */
    private ServerFriend getExistingFriend(final ServerFriend friend,
            final User user) {
        final String email = email(user);
        final PersistenceManager mgr = getPersistenceManager();
        try {
            final Query query = mgr.newQuery(ServerFriend.class);
            String str = "userEmail == '%s' && email == '%s'";
            str = String.format(str, email, friend.getEmail().toLowerCase());
            query.setFilter(str);
            query.setRange(0L, 1L);
            log.info("Querying for existing friend using: " + query);
            for (final Object obj : (List<Object>) query.execute()) {
                return (ServerFriend) obj;
            }
        } finally {
            mgr.close();
        }
        return null;
    }

    private void persist(final PersistenceManager mgr, final ServerFriend friend) {
        try {
            mgr.makePersistent(friend);
        } finally {
            mgr.close();
        }
    }

    /**
     * This method removes the entity with primary key id. It uses HTTP DELETE.
     * 
     * @param id The primary key of the entity to be deleted.
     * @throws UnauthorizedException If the user is unauthorized.
     */
    @ApiMethod(
            httpMethod = "DELETE",
            name = "friend.remove",
            path = "friend/remove/{id}")
    public void removeFriend(@Named("id") Long id, 
          final com.google.appengine.api.users.User user) 
                  throws UnauthorizedException {
        checkAuthorization(user);
        PersistenceManager mgr = getPersistenceManager();
        try {
            final ServerFriend friend = mgr.getObjectById(ServerFriend.class, id);
            mgr.deletePersistent(friend);
        } finally {
            mgr.close();
        }
    }
    

    /**
     * Normalizes email addresses.
     * 
     * @param user The user whose email address we want to normalize.
     * @return The normalized address.
     */
    private String email(final User user) {
        return user.getEmail().toLowerCase();
    }

    private void checkAuthorization(final User user) 
            throws UnauthorizedException {
        if (user == null) {
            log.warning("User is unauthorized!");
            throw new UnauthorizedException("Unauthorized");
        }
        /*
        final Dao dao = new Dao();
        final String email = email(user);
        if (!dao.isInvited(email)) {
            throw new UnauthorizedException("Unauthorized");
        }
        */
    }

    private static PersistenceManager getPersistenceManager() {
        return PMF.get().getPersistenceManager();
    }

}