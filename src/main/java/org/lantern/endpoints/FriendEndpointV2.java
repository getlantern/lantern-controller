package org.lantern.endpoints;

import java.util.List;

import javax.inject.Named;

import org.lantern.data.LanternFriend;
import org.lantern.messages.FriendResponse;
import org.lantern.state.Friend;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.UnauthorizedException;

/**
 * Endpoint for interacting with the friends of a given user, version 2.
 */
@Api(name = "friend",
        version = "v1",
        clientIds = { "323232879315-bea7ng41i8fsvua1takpcprbpd38nal9.apps.googleusercontent.com" },
        scopes = { "https://www.googleapis.com/auth/userinfo.email" })
public class FriendEndpointV2 extends BaseFriendEndpoint {

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
    public FriendResponse<List<Friend>> listFriend(
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        return doListFriend(user);
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
    public FriendResponse<Friend> getFriend(@Named("id") final Long id,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        return doGetFriend(id, user);
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
    public FriendResponse<Friend> insertFriend(final LanternFriend friend,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        return doInsertFriend(friend, user);
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
    public FriendResponse<Friend> updateFriend(final LanternFriend friend,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        return doUpdateFriend(friend, user);
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
    public FriendResponse<Void> removeFriend(@Named("id") final Long id,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        return doRemoveFriend(id, user);
    }
}