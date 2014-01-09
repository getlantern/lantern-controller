package org.lantern.endpoints;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.lantern.LanternControllerConstants;
import org.lantern.data.Dao;
import org.lantern.data.Dao.DbCall;
import org.lantern.data.FriendingQuota;
import org.lantern.data.LanternFriend;
import org.lantern.data.LanternUser;
import org.lantern.messages.FriendResponse;
import org.lantern.state.Friend;
import org.lantern.state.Friend.Status;

import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.users.User;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;

/**
 * Base class for Endpoints for interacting with the friends of a given user.
 */
public class BaseFriendEndpoint {

    private final transient Logger log = Logger.getLogger(getClass().getName());

    protected FriendResponse<List<Friend>> doListFriend(
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        checkAuthorization(user);

        log.info("Listing friends");
        final String userEmail = email(user);
        return new Dao()
                .withTransaction(new DbCall<FriendResponse<List<Friend>>>() {
                    @Override
                    public FriendResponse<List<Friend>> call(Objectify ofy) {
                        FriendingQuota quota = getOrCreateQuota(ofy, userEmail);
                        List<Friend> friends = new ArrayList<Friend>(
                                ofy.query(LanternFriend.class)
                                        .ancestor(quota)
                                        .list());
                        return success(quota, friends);
                    }
                });
    }

    protected FriendResponse<Friend> doGetFriend(final Long id,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        checkAuthorization(user);
        final String userEmail = email(user);

        FriendResponse<Friend> resp = new Dao()
                .withTransaction(new DbCall<FriendResponse<Friend>>() {
                    @Override
                    public FriendResponse<Friend> call(Objectify ofy) {
                        FriendingQuota quota = getOrCreateQuota(ofy, userEmail);
                        Friend existing = getExistingFriendById(ofy, quota, id);
                        return success(quota, existing);
                    }
                });

        if (!resp.payload().getUserEmail().toLowerCase().equals(userEmail)) {
            log.warning("Emails don't match?");
            throw new UnauthorizedException("Unauthorized");
        }
        return resp;
    }

    protected FriendResponse<Friend> doInsertFriend(final Friend friend,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        checkAuthorization(user);

        return new Dao().withTransaction(new DbCall<FriendResponse<Friend>>() {
            @Override
            public FriendResponse<Friend> call(Objectify ofy) {
                String userEmail = email(user);
                String friendEmail = friend.getEmail();
                log.info(userEmail + " is considering inserting friend "
                        + friend.getEmail());
                friend.setUserEmail(userEmail);

                FriendingQuota quota = getOrCreateQuota(ofy, userEmail);
                Friend existing = getExistingFriendByEmail(ofy,
                        quota,
                        friendEmail);
                if (existing != null) {
                    log.warning("Found existing friend, updating instead: " + existing.getEmail());
                    return doDoUpdateFriend(ofy, quota, friend, existing);
                }

                boolean friended = Friend.Status.friend == friend.getStatus();

                if (friended) {
                    if (!quota.checkAndIncrementTotalFriended()) {
                        log.info("Friending quota exceeded");
                        return failure(quota);
                    }
                }

                log.info("Inserting friend");
                ofy.put(friend);
                ofy.put(quota);

                if (friended) {
                    invite(friend);
                }

                return success(quota, friend);
            }
        });
    }

    protected FriendResponse<Friend> doUpdateFriend(final Friend friend,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        checkAuthorization(user);

        if (friend.getId() == null) {
            log.warning("No ID on friend? Ignoring update.");
            throw new RuntimeException("No id provided");
        }

        return new Dao().withTransaction(new DbCall<FriendResponse<Friend>>() {
            @Override
            public FriendResponse<Friend> call(Objectify ofy) {
                log.info("Updating friend...");
                String userEmail = email(user);
                FriendingQuota quota = getOrCreateQuota(ofy, userEmail);
                Friend existing = getExistingFriendById(ofy,
                        quota,
                        friend.getId());
                return doDoUpdateFriend(ofy, quota, friend, existing);
            }
        });
    }
    
    private FriendResponse<Friend> doDoUpdateFriend(Objectify ofy,
            FriendingQuota quota,
            Friend friend,
            Friend existing) {
        if (existing == null) {
            log.warning("Didn't find existing friend to update");
        } else {
            log.info("Found existing friend to update");
            Status priorStatus = existing.getStatus();
            boolean newlyFriended = Status.friend == friend.getStatus()
                    && Status.friend != priorStatus;
            if (newlyFriended) {
                if (!quota.checkAndIncrementTotalFriended()) {
                    log.info("Friending quota exceeded");
                    return failure(quota);
                }
            }
            existing.setEmail(friend.getEmail());
            existing.setName(friend.getName());
            existing.setStatus(friend.getStatus());
            existing.setLastUpdated(friend.getLastUpdated());
            ofy.put(existing);
            ofy.put(quota);
            if (newlyFriended) {
                invite(friend);
            }
        }
        return success(quota, friend);
    }

    protected FriendResponse<Void> doRemoveFriend(final Long id,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        checkAuthorization(user);
        return new Dao().withTransaction(new DbCall<FriendResponse<Void>>() {
            @Override
            public FriendResponse<Void> call(Objectify ofy) {
                String userEmail = email(user);
                FriendingQuota quota = getOrCreateQuota(ofy, userEmail);
                LanternFriend existing = getExistingFriendById(ofy, quota, id);
                ofy.delete(existing);
                return success(quota, null);
            }
        });
    }

    /**
     * Get an existing friend by their email address.
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
    private LanternFriend getExistingFriendByEmail(Objectify ofy,
            FriendingQuota quota,
            String friendEmail) {
        return ofy.query(LanternFriend.class)
                .ancestor(quota)
                .filter("email", friendEmail.toLowerCase())
                .get();
    }
    
    private LanternFriend getExistingFriendById(Objectify ofy,
            FriendingQuota quota,
            long id) {
        Key<FriendingQuota> parentKey =
                Key.create(FriendingQuota.class, quota.getEmail());
        Key<LanternFriend> key = Key.create(parentKey,
                LanternFriend.class, id);
        return ofy.find(key);
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
            int maxFriends = LanternControllerConstants.DEFAULT_MAX_FRIENDS
                    - user.getDegree();
            quota = new FriendingQuota(userEmail, maxFriends);
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
    }

    private void invite(Friend friend) {
        // TODO: we might want to do this in the same transaction as the rest
        // of the friending work, but we haven't been.  This would be a largish
        // refactoring to dao.addInviteAndApproveIfUnpaused, so I'm leaving it
        // alone for now.
        log.info("Inviting friend");
        final Dao dao = new Dao();
        dao.addInviteAndApproveIfUnpaused(
                friend.getUserEmail(), friend.getEmail(), null);
    }

    private <P> FriendResponse<P> success(FriendingQuota quota, P payload) {
        return new FriendResponse<P>(true, quota.getRemainingQuota(), payload);
    }

    private <P> FriendResponse<P> failure(FriendingQuota quota) {
        return new FriendResponse<P>(false, quota.getRemainingQuota(), null);
    }
}