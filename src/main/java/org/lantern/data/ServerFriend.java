package org.lantern.data;

import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
@PersistenceCapable
public class ServerFriend implements org.lantern.state.Friend {

    @PrimaryKey
    @Persistent(valueStrategy = IdGeneratorStrategy.IDENTITY)
    private Long id;
    
    @Persistent
    private String email;

    @Persistent
    private String name = "";
    
    @Persistent
    private String userEmail;

    @Persistent
    private Status status = Status.pending;
    
    @Persistent
    public Long lastUpdated = System.currentTimeMillis();

    /**
     * The next time, in milliseconds since epoch, that we will ask the user
     * about this friend, assuming status=requested.
     */
    private long nextQuery;

    /**
     * Whether or not an XMPP subscription request from this user is pending.
     */
    private boolean pendingSubscriptionRequest;

    public ServerFriend() {
    }

    public ServerFriend(String email) {
        this.email = email;
    }
    
    public void setLongId(Long id) {
        this.id = id;
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
        this.email = email;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
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
    public long getNextQuery() {
        return nextQuery;
    }

    @Override
    public void setNextQuery(long nextQuery) {
        this.nextQuery = nextQuery;
    }

    @Override
    public void setPendingSubscriptionRequest(boolean pending) {
        pendingSubscriptionRequest = pending;
    }

    @Override
    public boolean isPendingSubscriptionRequest() {
        return pendingSubscriptionRequest;
    }

    @JsonIgnore
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
    public String getUserEmail() {
        return userEmail;
    }

    @Override
    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    @Override
    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    @Override
    public long getLastUpdated() {
        return this.lastUpdated;
    }
}
