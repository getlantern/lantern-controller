package org.lantern.data;

import java.util.Date;
import java.util.logging.Logger;

import javax.persistence.Id;

import org.lantern.state.Mode;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Parent;

public class LanternInstance {

    private static final transient Logger log =
        Logger.getLogger(LanternInstance.class.getName());

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
     * Tracks the number of invites sent out with installers pointing to this
     * instance.
     *
     * Only makes sense for fallback proxies.
     */
    private int numberOfInvitesForFallback;

    /**
     * The location of the installers pointing to this instance.
     *
     * This is generated in the fallback proxies and unpacked in
     * FallbackProxyLauncher.sendInviteEmail.  See that one if you're
     * interested in the actual format.
     */
    private String installerLocation;

    /**
     * Is this a fallback proxy for which we have requested shutdown?
     *
     * We use this to issue warnings when users report this as their current
     * fallback proxy.
     */
    private boolean fallbackProxyShutdown;

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
        if (this.isFallbackProxy && !isFallbackProxy) {
            log.severe("No longer a fallback proxy?  What gives?");
        }
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

    public void incrementNumberOfInvitesForFallback(int increment) {
        if (increment < 0) {
            throw new RuntimeException("Actually decrementing?");
        }
        this.numberOfInvitesForFallback += increment;
    }

    public int getNumberOfInvitesForFallback() {
        return numberOfInvitesForFallback;
    }

    public boolean isCurrent() {
        long now = new Date().getTime();
        long age = now - lastUpdated.getTime();
        return age < 1000L * 60 * 15;
    }

    public String getInstallerLocation() {
        return installerLocation;
    }

    public void setInstallerLocation(final String installerLocation) {
        this.installerLocation = installerLocation;
    }

    public boolean isFallbackProxyShutdown() {
        return fallbackProxyShutdown;
    }

    public void setFallbackProxyShutdown(boolean fallbackProxyShutdown) {
        this.fallbackProxyShutdown = fallbackProxyShutdown;
    }
}
