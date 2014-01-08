package org.lantern.endpoints;

import java.util.List;

import javax.inject.Named;

import org.lantern.data.LanternFriend;
import org.lantern.state.Friend;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.UnauthorizedException;

/**
 * Endpoint for interacting with the friends of a given user.
 */
@Api(name = "friend",
        version = "v1",
        clientIds = { "323232879315-bea7ng41i8fsvua1takpcprbpd38nal9.apps.googleusercontent.com" },
        scopes = { "https://www.googleapis.com/auth/userinfo.email" })
public class FriendEndpoint extends BaseFriendEndpoint {

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
    public List<Friend> listFriend(
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        return doListFriend(user).payload();
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
    public Friend getFriend(@Named("id") final Long id,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        return doGetFriend(id, user).payload();
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
    public Friend insertFriend(final LanternFriend friend,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        return doInsertFriend(friend, user).payload();
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
    public Friend updateFriend(final LanternFriend friend,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        return doUpdateFriend(friend, user).payload();
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
    public void removeFriend(@Named("id") final Long id,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        doRemoveFriend(id, user);
    }
}