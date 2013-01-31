package org.lantern.data;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Id;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

public class LanternUser {
    
    @Id
    private String id;
    
    private long directBytes;
    
    private long bytesProxied;
    
    private long directRequests;
    
    private long requestsProxied;
    
    private Date created = new Date();

    private Set<String> countryCodes = new HashSet<String>();

    private SetMultimap<String, String> countryCodesByInstanceId = HashMultimap.create();

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

    private int instancesSignedIn = 0;
    
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

    /**
     * Adds a country to the list of countries for this user for this instanceId.
     * Returns true if the combination of instanceId and country has been seen
     * before for this user.
     * @param instanceId
     * @param countryCode
     * @return
     */
    public boolean instanceIdSeenFromCountry(final String instanceId, final String countryCode) {
        countryCodes.add(countryCode);
        return countryCodesByInstanceId.put(instanceId, countryCode);
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

    public boolean anyInstancesSignedIn() {
        return instancesSignedIn > 0;
    }

    public void incrementInstancesSignedIn() {
        instancesSignedIn ++;
    }

    public void decrementInstancesSignedIn() {
        instancesSignedIn --;
    }

    public boolean instanceIdSeen(String instanceId) {
        return countryCodesByInstanceId.containsKey(instanceId);
    }

    public boolean countrySeen(String countryCode) {
        return countryCodes.contains(countryCode);
    }
}
