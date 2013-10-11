package org.lantern.data;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.persistence.Id;

import com.googlecode.objectify.Key;


public class LanternUser implements Serializable {
    private static final long serialVersionUID = 1953109001251375722L;

    private final transient Logger log = Logger.getLogger(getClass().getName());

    @Id
    private String id;

    private long directBytes;

    private long bytesProxied;

    private long directRequests;

    private long requestsProxied;

    private Date created = new Date();

    private String countryCodes = "";

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
     * The instances we have seen running in behalf of this user.
     *
     * Instances are identified by XMPP resource, so if you want to use these
     * to key into the LanternInstances table, you need to obtain the full
     * jabberId. @see LanternControllerUtils.jabberIdFromUserAndResource .
     */
    private final HashSet<String> instanceIds = new HashSet<String>();


    private String name;

    private String refreshToken;

    /**
     * The ID of the Fallback Proxy used by this user's invitees.
     */
    private String fallbackProxyUserId;

    /**
     * instanceId of the fallback proxy to use for new invitees.
     *
     * null if we have never launched a fallback proxy to run as this user.
     *
     * LanternControllerConstants.FALLBACK_PROXY_LAUNCHING if we are currently
     * launching a fallback proxy to run as this user.
     */
    private String fallbackForNewInvitees;

    /**
     * A counter we keep to provide a unique serial number to all fallback launch
     * requests for this user.
     *
     * The cloudmaster needs this in the SQS message we send them in order to
     * make sure that for each SQS message it spawns one, and only one,
     * fallback proxy.
     */
    private int fallbackSerialNumber = 0;

    // TRANSITION: we use this to initialize the installerLocation of fallback
    // proxies that predate the fallback-balancing scheme.
    private String installerLocation;

    public LanternUser() {
        super();
    }

    public LanternUser(final String id) {
        super();
        this.id = id;
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

    public boolean instanceIdSeen(String instanceId) {
        return !instanceIds.add(instanceId);
    }

    public boolean countrySeen(String countryCode) {
        if (countryCodes == null) {
            countryCodes = countryCode + ".";
            return false;
        }
        if (countryCodes.contains(countryCode + ".")) {
            return true;
        } else {
            countryCodes += countryCode + ".";
            return false;
        }
    }

    public Set<String> getCountryCodes() {
        Set<String> out = new HashSet<String>();
        if (countryCodes != null) {
            for (String code : countryCodes.split("\\.")) {
                if (code.length() == 2) {
                    out.add(code);
                }
            }
        }
        return out;
    }

/*  Uncomment for datastore reset scripts.
    public void resetInstancesSignedIn() {
        instancesSignedIn = 0;
    }
    public void resetCountryCodes() {
        countryCodes = "";
    }
*/


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setFallbackProxyUserId(String fallbackProxyUserId) {
        this.fallbackProxyUserId = fallbackProxyUserId;
    }

    public String getFallbackProxyUserId() {
        return fallbackProxyUserId;
    }

    public void setFallbackForNewInvitees(String fallbackForNewInvitees) {
        this.fallbackForNewInvitees = fallbackForNewInvitees;
    }

    public String getFallbackForNewInvitees() {
        return fallbackForNewInvitees;
    }

    public void setInstallerLocation(String installerLocation) {
        this.installerLocation = installerLocation;
    }

    public String getInstallerLocation() {
        return installerLocation;
    }

    public int incrementFallbackSerialNumber() {
        fallbackSerialNumber += 1;
        return fallbackSerialNumber;
    }
}
