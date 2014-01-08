package org.lantern.data;

import javax.persistence.Entity;
import javax.persistence.Id;

import com.google.appengine.repackaged.org.codehaus.jackson.annotate.JsonIgnore;
import com.google.appengine.repackaged.org.codehaus.jackson.annotate.JsonIgnoreProperties;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Parent;

@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
public class LanternFriend implements org.lantern.state.Friend {

    @Parent
    @JsonIgnore
    private Key<FriendingQuota> quota;

    @Id
    private Long id;
    private String email;
    private String userEmail;
    private Status status = Status.pending;
    public Long lastUpdated = System.currentTimeMillis();
    private String name;

    public LanternFriend() {
    }

    public LanternFriend(String email) {
        this.email = email;
    }

    public void setLongId(Long id) {
        this.id = id;
    }

    @JsonIgnore
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
        this.email = email;
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
        this.userEmail = userEmail.trim().toLowerCase();
        // Derive the FriendingQuota key from the user email
        if (this.userEmail.length() > 0) {
            this.setQuota(Key.create(FriendingQuota.class, this.userEmail));
        } else {
            this.setQuota(null);
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
    public String toString() {
        return "Friend(" + email + ")";
    }
}
