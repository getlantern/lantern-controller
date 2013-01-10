package org.lantern.data;

import java.util.Date;
import java.util.Set;

import javax.persistence.Id;

public class LanternUser {
    
    @Id
    private String id;
    
    private long directBytes;
    
    private long bytesProxied;
    
    private long directRequests;
    
    private long requestsProxied;
    
    private Date created = new Date();
    
    private Set<String> countryCodes;
    
    private int invites;

    /**
     * This is the number of degrees this user is from the original core of
     * Lantern developers.
     */
    private int degree;
    
    /**
     * This is the user who invited this user to the network.
     */
    private String sponsor;
    
    private boolean everSignedIn = false;
    
    private Date lastAccessed = new Date();
    
    /**
     * The server, if any, that is running in behalf of this user to provide
     * installers and fallback connectivity to the users she invited.
     *
     * Possible values are:
     *
     *  - null if this user has never invited anyone yet,
     *  - 'launching' if the server is still being launched, or
     *  - the IP address where the server is running, otherwise.
     */
    //XXX: Disabled because we are not using this at the moment, but we'll
    // probably need it when we want to recover from crashed/blocked invited
    // servers and buckets.
    //private String invitedServer;

    /**
     * The location where invitees of this user should get their installers.
     *
     * This is in `bucket/folder` format, where bucket is the name of an
     * Amazon S3 Bucket and folder is the name of a folder in that bucket,
     * which contains the installers.
     */
    private String installerLocation;

    public LanternUser() {
        super();
    }

    public LanternUser(final String id) {
        super();
        this.id = id;
        this.invites = 0;
    }

    public String getId() {
        return id;
    }

    public void setCreated(final Date created) {
        this.created = created;
    }

    public Date getCreated() {
        return created;
    }

    public long getBytesProxied() {
        return bytesProxied;
    }

    public void setBytesProxied(final long bytesProxied) {
        this.bytesProxied = bytesProxied;
    }

    public Set<String> getCountryCodes() {
        return countryCodes;
    }

    public void setCountryCodes(final Set<String> countryCodes) {
        this.countryCodes = countryCodes;
    }
    
    public long getDirectBytes() {
        return directBytes;
    }

    public void setDirectBytes(long directBytes) {
        this.directBytes = directBytes;
    }

    public long getDirectRequests() {
        return directRequests;
    }

    public void setDirectRequests(long directRequests) {
        this.directRequests = directRequests;
    }

    public long getRequestsProxied() {
        return requestsProxied;
    }

    public void setRequestsProxied(long requestsProxied) {
        this.requestsProxied = requestsProxied;
    }

    public void setInvites(int invites) {
        this.invites = invites;
    }

    public int getInvites() {
        return invites;
    }

    public void setDegree(int degree) {
        this.degree = degree;
    }

    public int getDegree() {
        return degree;
    }

    public String getSponsor() {
        return sponsor;
    }

    public void setSponsor(String sponsor) {
        this.sponsor = sponsor;
    }

    public boolean isEverSignedIn() {
        return everSignedIn;
    }

    public void setEverSignedIn(boolean everSignedIn) {
        this.everSignedIn = everSignedIn;
    }

    public Date getLastAccessed() {
        return lastAccessed;
    }

    public void setLastAccessed(Date lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    public String getInstallerLocation() {
        return installerLocation;
    }

    public void setInstallerLocation(final String installerLocation) {
        this.installerLocation = installerLocation;
    }

    //XXX: Disabled because we are not using this at the moment, but we'll
    // probably need it when we want to recover from crashed/blocked invited
    // servers and buckets.
    /*public String getInvitedServer() {
        return invitedServer;
    }

    public void setInvitedServer(String ip) {
        invitedServer = ip;
    }*/
}
