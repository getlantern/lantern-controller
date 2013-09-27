package org.lantern.data;

import java.io.Serializable;

import org.apache.commons.lang3.StringUtils;


public class LegacyFriend implements Serializable {
    private static final long serialVersionUID = 6669786580088595294L;

    private String email;

    private String name = "";

    public enum Status {
        friend,
        rejected,
        pending //everything else
    }

    private Status status = Status.pending;

    /**
     * The last time the status was updated by a user action or request, in
     * milliseconds since epoch
     */
    private long lastUpdated;

    /**
     * The next time, in milliseconds since epoch, that we will ask the user
     * about this friend, assuming status=requested.
     */
    private long nextQuery;

    /**
     * Whether or not an XMPP subscription request from this user is pending.
     */
    private boolean pendingSubscriptionRequest;

    public LegacyFriend() {
        lastUpdated = System.currentTimeMillis();
    }

    public LegacyFriend(String email) {
        lastUpdated = System.currentTimeMillis();
        this.setEmail(email);
    }

    public LegacyFriend(String email, Status status, String name, long nextQuery, long lastUpdated) {
        this.email = email;
        this.status = status;
        this.name = name;
        this.nextQuery = nextQuery;
        this.lastUpdated = lastUpdated;
    }

    public void update(LegacyFriend other) {
        this.status = other.status;
        this.name = other.name;
        this.nextQuery = other.nextQuery;
        this.lastUpdated = other.lastUpdated;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        String oldName = this.name;
        this.name = name;
        if (!StringUtils.equals(name, oldName)) {
            updated();
        }
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        Status oldStatus = this.status;
        this.status = status;
        if (status != oldStatus) {
            updated();
        }
    }

    private void updated() {
        this.setLastUpdated(System.currentTimeMillis());
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public long getNextQuery() {
        return nextQuery;
    }

    public void setNextQuery(long nextQuery) {
        this.nextQuery = nextQuery;
    }

    public void setPendingSubscriptionRequest(boolean pending) {
        pendingSubscriptionRequest = pending;
    }

    public boolean isPendingSubscriptionRequest() {
        return pendingSubscriptionRequest;
    }

    public boolean shouldNotifyAgain() {
        if (status == Status.pending) {
            long now = System.currentTimeMillis();
            return nextQuery < now;
        }
        return false;
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
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        LegacyFriend other = (LegacyFriend) obj;
        if (email == null) {
            if (other.email != null)
                return false;
        } else if (!email.equals(other.email))
            return false;
        return true;
    }
}