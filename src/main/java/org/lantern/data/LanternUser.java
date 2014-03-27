package org.lantern.data;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.persistence.Id;

import com.googlecode.objectify.Key;


public class LanternUser implements Serializable {
    private static final long serialVersionUID = 1953109001251375722L;

    @Id
    private String id;
    
    private String guid;

    private long bytesProxied;

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
     * The fallback proxy configured for this user.
     */
    private Key<FallbackProxy> fallback;

    /**
     * Transitioning out of this...
     */
    private Key<LanternInstance> fallbackProxy;

    /**
     * Transitioning out of this...
     */
    private String fallbackForNewInvitees;

    /**
     * Transitioning out of this...
     */
    private int fallbackSerialNumber = 0;

    /**
     * Transitioning out of this...
     */
    private String installerLocation;
    
    private int generation = 0;

    /**
     * S3 path suffix of the wrappers and config files for this user.
     */
    private String configFolder;

    /**
     * Whether the wrappers for this user have been reported as uploaded.
     */
    private boolean wrappersUploaded = false;

    public LanternUser() {
        super();
    }

    public LanternUser(final String id) {
        super();
        this.id = id;
        // New users get generation 1
        this.generation = 1;
    }

    public String getId() {
        return id;
    }
    
    /**
     * GUID identifying this user (primarily used for statistics purposes).
     * 
     * This is a type 4 guid which has a very low collision rate, but as with
     * any GUID, collisions are possible.
     * @return
     */
    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }
    
    public long getBytesProxied() {
        return bytesProxied;
    }
    
    public void setBytesProxied(long bytesProxied) {
        this.bytesProxied = bytesProxied;
    }
    
    /**
     * Initializes the guid field if necessary.
     * 
     * @return true if the guid had to be initialized
     */
    public boolean initializeGuidIfNecessary() {
        if (guid == null) {
            guid = UUID.randomUUID().toString();
            return true;
        }
        return false;
    }
    
    public void setCreated(final Date created) {
        this.created = created;
    }

    public Date getCreated() {
        return created;
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

    public void setConfigFolder(String configFolder) {
        this.configFolder = configFolder;
    }

    public String getConfigFolder() {
        return configFolder;
    }

    public void setWrappersUploaded() {
        wrappersUploaded = true;
    }

    public boolean getWrappersUploaded() {
        return wrappersUploaded;
    }

    public int incrementFallbackSerialNumber() {
        fallbackSerialNumber += 1;
        return fallbackSerialNumber;
    }

    public Key<FallbackProxy> getFallback() {
        return fallback;
    }

    public void setFallback(Key<FallbackProxy> fallback) {
        this.fallback = fallback;
    }

    public Key<LanternInstance> getFallbackProxy() {
        return fallbackProxy;
    }

    public void setFallbackProxy(Key<LanternInstance> fallbackProxy) {
        this.fallbackProxy = fallbackProxy;
    }
    
    public int getGeneration() {
        return generation;
    }
    
    public void setGeneration(int generation) {
        this.generation = generation;
    }
}
