package org.lantern.data;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.Id;
import javax.persistence.Transient;

import org.lantern.EmailAddressUtils;
import org.lantern.loggly.LoggerFactory;
import org.lantern.state.Friend;

import com.google.appengine.repackaged.org.codehaus.jackson.annotate.JsonIgnore;
import com.google.appengine.repackaged.org.codehaus.jackson.annotate.JsonIgnoreProperties;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Parent;

/**
 * <p>
 * Maps a friend relationship between a user identified by {@link #userEmail}
 * and the friend's {@link #email}. Depending on the {@link #status}, the pair
 * represented here may or may not actually be friends.
 * </p>
 * 
 * <p>
 * This class replaces the old {@link ServerFriend} class.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@org.codehaus.jackson.annotate.JsonIgnoreProperties(ignoreUnknown = true)
public class LanternFriend implements Friend {
    private static final Logger LOGGER = LoggerFactory.getLogger(LanternFriend.class);

    @Parent
    @JsonIgnore
    @org.codehaus.jackson.annotate.JsonIgnore
    private Key<FriendingQuota> quota;

    @Id
    private Long id;
    private String email;
    private String userEmail;
    private Status status = Status.pending;
    public Long lastUpdated = System.currentTimeMillis();
    private String name;
    @Transient
    private boolean freeToFriend = false;
    private SuggestionReason reason;

    public LanternFriend() {
    }

    public LanternFriend(String email) {
        this.email = EmailAddressUtils.normalizedEmail(email);
    }

    public static LanternFriend reverseOf(Friend friendedBy) {
        LanternFriend friend = new LanternFriend(friendedBy.getUserEmail());
        friend.setUserEmail(friendedBy.getEmail());
        friend.setStatus(Status.pending);
        friend.setFreeToFriend(true);
        friend.setReason(SuggestionReason.friendedYou);
        return friend;
    }
    
    @JsonIgnore
    @org.codehaus.jackson.annotate.JsonIgnore
    public Key<FriendingQuota> getQuota() {
        return quota;
    }

    public void setQuota(Key<FriendingQuota> quota) {
        this.quota = quota;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public void setEmail(String email) {
        this.email = EmailAddressUtils.normalizedEmail(email);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public String getUserEmail() {
        return userEmail;
    }

    @Override
    public void setUserEmail(String userEmail) {
        try {
            this.userEmail = EmailAddressUtils.normalizedEmail(userEmail);
            // Derive the FriendingQuota key from the user email
            if (this.userEmail.length() > 0) {
                this.setQuota(Key.create(FriendingQuota.class, this.userEmail));
            } else {
                this.setQuota(null);
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE,
                    String.format("Unable to set userEmail: %s, exception: %s",
                            userEmail, e.getMessage()), e);
            throw e;
        }
    }

    @Override
    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    @Override
    public long getLastUpdated() {
        return this.lastUpdated;
    }

    @Override
    public void setFreeToFriend(boolean freeToFriend) {
        this.freeToFriend = freeToFriend;
    }

    @Override
    public boolean isFreeToFriend() {
        return this.freeToFriend;
    }

    @Override
    public SuggestionReason getReason() {
        return reason;
    }

    @Override
    public void setReason(SuggestionReason reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "Friend(" + email + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((email == null) ? 0 : email.hashCode());
        result = prime * result
                + ((userEmail == null) ? 0 : userEmail.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LanternFriend other = (LanternFriend) obj;
        if (email == null) {
            if (other.email != null)
                return false;
        } else if (!email.equals(other.email))
            return false;
        if (userEmail == null) {
            if (other.userEmail != null)
                return false;
        } else if (!userEmail.equals(other.userEmail))
            return false;
        return true;
    }

    public void normalizeEmails() {
        email = EmailAddressUtils.normalizedEmail(email);
        userEmail = EmailAddressUtils.normalizedEmail(userEmail);
    }
}
