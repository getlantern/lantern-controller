package org.lantern.endpoints;

import javax.inject.Named;

import org.lantern.data.LanternFriend;
import org.lantern.friending.Friending;
import org.lantern.messages.FriendResponse;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.UnauthorizedException;

/**
 * Endpoint for interacting with the friends of a given user, version 2.
 */
@Api(name = "friend",
        version = "v2",
        clientIds = { "323232879315-bea7ng41i8fsvua1takpcprbpd38nal9.apps.googleusercontent.com" },
        scopes = { "https://www.googleapis.com/auth/userinfo.email" })
public class FriendEndpointV2 {

    /**
     * This method lists all the entities inserted in datastore. It uses HTTP
     * GET method.
     * 
     * @return List of all entities persisted.
     * @throws UnauthorizedException
     *             If the user is unauthorized.
     */
    @ApiMethod(
            httpMethod = "GET",
            name = "friend.list",
            path = "friend/list")
    public FriendResponse listFriend(
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        Endpoints.checkAuthorizationAndCreateUser(user);
        return Friending.listFriend(user);
    }

    /**
     * This method gets the entity having primary key id. It uses HTTP GET
     * method.
     * 
     * @param id
     *            The primary key of the java bean.
     * @return The entity with primary key id.
     * @throws UnauthorizedException
     *             If the user is unauthorized.
     */
    @ApiMethod(
            httpMethod = "GET",
            name = "friend.get",
            path = "friend/get/{id}")
    public FriendResponse getFriend(@Named("id") final Long id,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        Endpoints.checkAuthorizationAndCreateUser(user);
        return Friending.getFriend(id, user);
    }

    /**
     * This inserts the entity into App Engine datastore. It uses HTTP POST.
     * 
     * @param task
     *            The entity to be inserted.
     * @return The inserted entity.
     * @throws UnauthorizedException
     *             If the user is unauthorized.
     */
    @ApiMethod(
            httpMethod = "POST",
            name = "friend.insert",
            path = "friend/insert")
    public FriendResponse insertFriend(final LanternFriend friend,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        Endpoints.checkAuthorizationAndCreateUser(user);
        friend.normalizeEmails();
        return Friending.insertFriend(friend, user);
    }

    /**
     * This method is used for updating an entity. It uses the HTTP PUT method.
     * 
     * @param friend
     *            The entity to be updated.
     * @return The updated entity.
     * @throws UnauthorizedException
     *             If the user is unauthorized.
     */
    @ApiMethod(
            httpMethod = "POST",
            name = "friend.update",
            path = "friend/update")
    public FriendResponse updateFriend(final LanternFriend friend,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        Endpoints.checkAuthorizationAndCreateUser(user);
        friend.normalizeEmails();
        return Friending.updateFriend(friend, user);
    }

    /**
     * This method removes the entity with primary key id. It uses HTTP DELETE.
     * 
     * @param id
     *            The primary key of the entity to be deleted.
     * @throws UnauthorizedException
     *             If the user is unauthorized.
     */
    @ApiMethod(
            httpMethod = "DELETE",
            name = "friend.remove",
            path = "friend/remove/{id}")
    public FriendResponse removeFriend(@Named("id") final Long id,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        Endpoints.checkAuthorizationAndCreateUser(user);
        return Friending.removeFriend(id, user);
    }
}
