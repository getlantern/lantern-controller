package org.lantern.data;

import java.util.Date;

import javax.persistence.Id;

import org.lantern.state.Mode;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Parent;

public class LanternInstance {
    @Id
    private String id;

    @Parent
    private Key<LanternUser> parent;

    private boolean available;

    private Date lastUpdated = new Date();

    private String user;

    private String countries = "";

    private String currentCountry;
    
    /* The most recent resource id we have seen for this instance. */
    private String resource;

    private Mode mode;
    
    private boolean isFallbackProxy;
    
    /**
     * The host and port on which this proxy (Give mode) is listening.
     */
    private String listenHostAndPort;
    
    /**
     * The host and port for the fallback proxy used by this instance (Get mode).
     */
    private String fallbackProxyHostAndPort;
    
    /**
     * Tracks the number of invitees for this fallback proxy (only applies to
     * fallback proxy instances).
     */
    private int numberOfInviteesForFallback;

    public LanternInstance() {
        super();
    }

    public LanternInstance(final String id, final Key<LanternUser> parent) {
        super();
        this.id = id;
        this.parent = parent;
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
        if (countries.contains(countryCode + ".")) {
            return;
        }
        countries += countryCode + ".";
    }

    public boolean getSeenFromCountry(String countryCode) {
        return countries.contains(countryCode + ".");
    }

    public String getCurrentCountry() {
        return currentCountry;
    }

    public void setCurrentCountry(String currentCountry) {
        this.currentCountry = currentCountry;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }
    
    public void setFallbackProxy(boolean isFallbackProxy) {
        this.isFallbackProxy = isFallbackProxy;
    }
    
    public boolean isFallbackProxy() {
        return isFallbackProxy;
    }
    
    public void setListenHostAndPort(String listenHostAndPort) {
        this.listenHostAndPort = listenHostAndPort;
    }
    
    public String getListenHostAndPort() {
        return listenHostAndPort;
    }
    
    public void setFallbackProxyHostAndPort(String fallbackProxyHostAndPort) {
        this.fallbackProxyHostAndPort = fallbackProxyHostAndPort;
    }
    
    public String getFallbackProxyHostAndPort() {
        return fallbackProxyHostAndPort;
    }
    
    public void setNumberOfInviteesForFallback(int numberOfInviteesForFallback) {
        this.numberOfInviteesForFallback = numberOfInviteesForFallback;
    }
    
    public int getNumberOfInviteesForFallback() {
        return numberOfInviteesForFallback;
    }
    
    public boolean isCurrent() {
        long now = new Date().getTime();
        long age = now - lastUpdated.getTime();
        return age < 1000L * 60 * 15;
    }

}
