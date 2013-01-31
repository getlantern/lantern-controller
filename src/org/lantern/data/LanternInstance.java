package org.lantern.data;

import java.util.Date;
import java.util.HashSet;

import javax.persistence.Embedded;
import javax.persistence.Id;

public class LanternInstance {
    @Id
    private String id;
    
    private boolean available;
    
    private Date lastUpdated = new Date();
    
    @Embedded private LanternUser user;

    private HashSet<String> countries = new HashSet<String>();

    private String currentCountry;
    
    public LanternInstance() {
        super();
    }

    public LanternInstance(final String id) {
        super();
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(final boolean available) {
        this.available = available;
        if (available)
            user.incrementInstancesSignedIn();
        else
            user.decrementInstancesSignedIn();
    }
    
    public boolean getAvailable() {
        return this.available;
    }

    public void setUser(final LanternUser user) {
        this.user = user;
    }

    public LanternUser getUser() {
        return user;
    }

    public void setLastUpdated(final Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void addSeenFromCountry(String countryCode) {
        countries.add(countryCode);
    }

    public boolean getSeenFromCountry(String countryCode) {
        return countries.contains(countryCode);
    }

    public String getCurrentCountry() {
        return currentCountry;
    }

    public void setCurrentCountry(String currentCountry) {
        this.currentCountry = currentCountry;
    }
}
