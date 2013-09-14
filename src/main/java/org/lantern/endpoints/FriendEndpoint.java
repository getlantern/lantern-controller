package org.lantern.endpoints;

import java.util.ArrayList;
import java.util.List;

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
        
        final String email = user.getEmail();
        final PersistenceManager mgr = getPersistenceManager();
        final List<Friend> result = new ArrayList<Friend>();
        try {
            final Query query = mgr.newQuery(ServerFriend.class);
            query.setFilter("userEmail == '"+email+"'");
            for (Object obj : (List<Object>) query.execute()) {
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
            if (!friend.getUserEmail().equals(user.getEmail())) {
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
        friend.setUserEmail(user.getEmail());
        PersistenceManager mgr = getPersistenceManager();
        try {
            mgr.makePersistent(friend);
        } finally {
            mgr.close();
        }
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
        friend.setUserEmail(user.getEmail());
        PersistenceManager mgr = getPersistenceManager();
        try {
            mgr.makePersistent(friend);
        } finally {
            mgr.close();
        }
        return friend;
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
    

    private void checkAuthorization(final User user) 
            throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        /*
        final Dao dao = new Dao();
        final String email = user.getEmail();
        if (!dao.isInvited(email)) {
            throw new UnauthorizedException("Unauthorized");
        }
        */
    }

    private static PersistenceManager getPersistenceManager() {
        return PMF.get().getPersistenceManager();
    }

}