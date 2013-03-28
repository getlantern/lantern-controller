package org.lantern.data;

import java.util.Date;
import java.util.HashSet;

import javax.persistence.Id;

public class LanternInstance {
    @Id
    private String id;

    private boolean available;

    private Date lastUpdated = new Date();

    private String user;

    private final HashSet<String> countries = new HashSet<String>();

    private String currentCountry;

    private boolean giveMode;

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
    }

    public boolean getAvailable() {
        return this.available;
    }

    public void setUser(final String user) {
        this.user = user;
    }

    public String getUser() {
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

    public boolean isGiveMode() {
        return giveMode;
    }

    public void setGiveMode(boolean mode) {
        this.giveMode = mode;
    }
}
