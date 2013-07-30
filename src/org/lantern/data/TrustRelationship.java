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
        return lastUpdated > friend.getLastUpdated();
    }

    public String getId() {
        return id;
    }
}
