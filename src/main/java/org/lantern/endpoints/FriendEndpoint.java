package org.lantern.endpoints;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Named;

import org.lantern.LanternControllerConstants;
import org.lantern.data.Dao;
import org.lantern.data.Dao.DbCall;
import org.lantern.data.FriendingQuota;
import org.lantern.data.LanternFriend;
import org.lantern.data.LanternUser;
import org.lantern.state.Friend;
import org.lantern.state.Friend.Status;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.users.User;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;

/**
 * Endpoint for interacting with the friends of a given user.
 */
@Api(name = "friend",
        version = "v1",
        clientIds = { "323232879315-bea7ng41i8fsvua1takpcprbpd38nal9.apps.googleusercontent.com" },
        scopes = { "https://www.googleapis.com/auth/userinfo.email" })
public class FriendEndpoint {

    private final transient Logger log = Logger.getLogger(getClass().getName());

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
        checkAuthorization(user);

        log.info("Listing friends");
        final String userEmail = email(user);
        return new Dao().withTransaction(new DbCall<List<Friend>>() {
            @Override
            public List<Friend> call(Objectify ofy) {
                FriendingQuota quota = getOrCreateQuota(ofy, userEmail);
                return new ArrayList<Friend>(
                        ofy.query(LanternFriend.class)
                                .ancestor(quota)
                                .list());
            }
        });
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
        checkAuthorization(user);
        final String userEmail = email(user);

        Friend friend = new Dao().withObjectify(new DbCall<Friend>() {
            @Override
            public Friend call(Objectify ofy) {
                Key<FriendingQuota> parentKey =
                        Key.create(FriendingQuota.class, userEmail);
                Key<LanternFriend> key = Key.create(parentKey,
                        LanternFriend.class, id);
                return ofy.find(key);
            }
        });

        if (!friend.getUserEmail().toLowerCase().equals(userEmail)) {
            log.warning("Emails don't match?");
            throw new UnauthorizedException("Unauthorized");
        }
        return friend;
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
        checkAuthorization(user);

        return new Dao().withTransaction(new DbCall<LanternFriend>() {
            @Override
            public LanternFriend call(Objectify ofy) {
                String userEmail = email(user);
                String friendEmail = friend.getEmail();
                log.info(userEmail + " is considering inserting friend "
                        + friend.getEmail());
                friend.setUserEmail(userEmail);

                FriendingQuota quota = getOrCreateQuota(ofy, userEmail);
                LanternFriend existing = getExistingFriend(ofy,
                        quota,
                        friendEmail);
                if (existing != null) {
                    log.warning("Found existing friend " + existing.getEmail());
                    return existing;
                }

                boolean friended = Friend.Status.friend == friend.getStatus();

                if (friended) {
                    if (!quota.checkAndIncrement()) {
                        // TODO: return something meaningful here
                        throw new RuntimeException("Hit limit");
                    }
                }

                log.info("Inserting friend");
                ofy.put(friend);
                ofy.put(quota);

                if (friended) {
                    invite(friend);
                }

                return friend;
            }
        });
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
        checkAuthorization(user);

        if (friend.getId() == null) {
            log.warning("No ID on friend? Ignoring update.");
            return friend;
        }

        return new Dao().withTransaction(new DbCall<LanternFriend>() {
            @Override
            public LanternFriend call(Objectify ofy) {
                log.info("Updating friend...");
                String userEmail = email(user);
                String friendEmail = friend.getEmail();
                FriendingQuota quota = getOrCreateQuota(ofy, userEmail);
                LanternFriend existing = getExistingFriend(ofy,
                        quota,
                        friendEmail);
                if (existing != null) {
                    log.info("Found existing friend");
                    Status priorStatus = existing.getStatus();
                    boolean newlyFriended = Status.friend == friend.getStatus()
                            && Status.friend != priorStatus;
                    if (newlyFriended) {
                        if (!quota.checkAndIncrement()) {
                            // TODO: return something meaningful here
                            throw new RuntimeException("Hit limit");
                        }
                    }
                    existing.setName(friend.getName());
                    existing.setStatus(friend.getStatus());
                    existing.setLastUpdated(friend.getLastUpdated());
                    ofy.put(existing);
                    ofy.put(quota);
                    if (newlyFriended) {
                        invite(friend);
                    }
                    return existing;
                }
                return friend;
            }
        });
    }

    /**
     * Check for an existing friend to avoid duplicate friends that somehow are
     * creeping into the database.
     * 
     * @param ofy
     *            the Objectify instance
     * @param quota
     *            The FriendingQuota for the logged-in user
     * @param user
     *            The user with the given friend.
     * @return The existing friend or <code>null</code> if no such friend
     *         exists.
     */
    private LanternFriend getExistingFriend(Objectify ofy,
            FriendingQuota quota,
            String friendEmail) {
        return ofy.query(LanternFriend.class)
                .ancestor(quota)
                .filter("email", friendEmail.toLowerCase())
                .get();
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
        checkAuthorization(user);
        new Dao().withTransaction(new DbCall<Void>() {
            @Override
            public Void call(Objectify ofy) {
                String userEmail = email(user);
                LanternUser lanternUser = ofy.find(LanternUser.class,
                        userEmail);
                Key<LanternUser> parentKey = Key.create(LanternUser.class,
                        lanternUser.getId());
                Key<LanternFriend> key = Key.create(parentKey,
                        LanternFriend.class, id);
                LanternFriend existing = ofy.get(key);
                ofy.delete(existing);
                return null;
            }
        });
    }

    /**
     * Gets or creates a FriendingQuota for the given userEmail.
     * 
     * @param ofy
     * @param userEmail
     * @return
     */
    private FriendingQuota getOrCreateQuota(Objectify ofy, String userEmail) {
        Key<FriendingQuota> quotaKey =
                Key.create(FriendingQuota.class, userEmail);
        FriendingQuota quota = ofy.find(quotaKey);
        if (quota == null) {
            // Create a new quota for this user
            LanternUser user = ofy.find(LanternUser.class, userEmail);
            quota = new FriendingQuota(
                    userEmail,
                    LanternControllerConstants.DEFAULT_MAX_FRIENDS
                            - user.getDegree());
            ofy.put(quota);
        }
        return quota;
    }

    /**
     * Normalizes email addresses.
     * 
     * @param user
     *            The user whose email address we want to normalize.
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
         * final Dao dao = new Dao(); final String email = email(user); if
         * (!dao.isInvited(email)) { throw new
         * UnauthorizedException("Unauthorized"); }
         */
    }

    private void invite(Friend friend) {
        log.info("Inviting friend");
        final Dao dao = new Dao();
        dao.addInviteAndApproveIfUnpaused(
                friend.getUserEmail(), friend.getEmail(), null);
    }

}