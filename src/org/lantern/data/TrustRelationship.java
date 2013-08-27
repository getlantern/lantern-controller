package org.lantern.data;

import java.io.Serializable;

import javax.persistence.Id;

import org.lantern.state.Friend;
import org.lantern.state.Friend.Status;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Parent;

/**
 * A trust relationship is a relationship between two lantern users: a user who
 * is trusted by another user, or a user who is implicitly untrusted by said
 * other user.
 *
 */
public class TrustRelationship implements Serializable {
    private static final long serialVersionUID = 6174397205170043683L;

    /** actually, just the email address of the friend
     * (with the parent, this is sufficient for uniqueness) */
    @Id
    private String id;

    /**
     * App Engine won't let me filter on id, so instead I have to
     * create this otherwise pointless field.  Thanks, App Engine!
     */
    private String duplicateOfId;

    @Parent
    private Key<LanternUser> parent;

    //private String owner;
    //private String friend;

    private Status status;

    private long lastUpdated;

    public TrustRelationship() {
        // for ofy
    }

    public TrustRelationship(Key<LanternUser> owner, Friend friend) {
        this.parent = owner;
        this.id = friend.getEmail();
        this.duplicateOfId = id;
        this.status = friend.getStatus();
        this.lastUpdated = friend.getLastUpdated();
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    /**
     * Updates this friend from the client's version. If this version was
     * updated more recently, no changes are made.
     *
     * @param friend
     * @return whether this needs to be persisted
     */
    public boolean update(Friend friend) {
        if (isNewerThan(friend)) {
            return false;
        }

        if (this.status != friend.getStatus()) {
            this.status = friend.getStatus();
            this.lastUpdated = friend.getLastUpdated();
            return true;
        }
        return false;
    }

    public boolean isNewerThan(Friend friend) {
        // this is actually a bit complicated. We want "friend" or "rejected"
        // to override "pending", because "pending" friends are only
        // automatically generated, while "friend" or "rejected" requires
        // user action.
        if ((status == Status.pending) == (friend.getStatus() == Status.pending)) {
            //both are pending or neither are
            return lastUpdated > friend.getLastUpdated();
        }

        return friend.getStatus() == Status.pending;
    }

    public String getId() {
        return id;
    }

    public String getDuplicateOfId() {
        return duplicateOfId;
    }

    public void setDuplicateOfId(
            String duplicateOfId) {
        this.duplicateOfId = duplicateOfId;
    }

    public Key<LanternUser> getParent() {
        return parent;
    }
}
