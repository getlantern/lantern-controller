package org.lantern.data;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.persistence.Id;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.lantern.state.Friend;
import org.lantern.state.Friends;


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

    private final Set<String> countryCodes = new HashSet<String>();

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

    /**
     * The instances we have seen running in behalf of this user.
     *
     * Instances are identified by XMPP resource, so if you want to use these
     * to key into the LanternInstances table, you need to obtain the full
     * jabberId. @see LanternControllerUtils.jabberIdFromUserAndResource .
     */
    private final HashSet<String> instanceIds = new HashSet<String>();

    /**
     * The location where invitees of this user should get their installers.
     *
     * This is in `bucket/folder` format, where bucket is the name of an
     * Amazon S3 Bucket and folder is the name of a folder in that bucket,
     * which contains the installers.
     */
    private String installerLocation;

    private String serializedFriends;

    private String name;

    private String refreshToken;

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
        //if instancesSignedIn is zero, there is a bug
        if (instancesSignedIn > 0)
            instancesSignedIn --;
        else
            log.warning("Instances signed in for " + this + " is already zero");
    }

    public boolean instanceIdSeen(String instanceId) {
        return !instanceIds.add(instanceId);
    }

    public boolean countrySeen(String countryCode) {
        return !countryCodes.add(countryCode);
    }

    public Set<String> getCountryCodes() {
        return countryCodes;
    }

/*  Uncomment for datastore reset scripts.
    public void resetInstancesSignedIn() {
        instancesSignedIn = 0;
    }
    public void resetCountryCodes() {
        countryCodes = new HashSet<String>();
    }
*/

    public String getInstallerLocation() {
        return installerLocation;
    }

    public void setInstallerLocation(final String installerLocation) {
        this.installerLocation = installerLocation;
    }


    class SyncResult {
        public List<Friend> changed = new ArrayList<Friend>();
        public boolean shouldSave = false;
    }

    /**
     * @param clientFriends a list of friend objects that the client has sent
     * @return a list of friend objects that have been changed on the server
     * more recently than on the client, along with an indication of whether
     * any data on the server side has changed.
     */
    public synchronized SyncResult syncFriendsFromClient(Friends clientFriends) {

        List<Friend> friends = getFriends();
        SyncResult result = new SyncResult();

        Map<String, Friend> friendMap = new HashMap<String, Friend>();
        for (Friend friend : friends) {
            friendMap.put(friend.getEmail(), friend);
        }

        log.info("I know about " + friends.size() + " friends");
        for (Friend clientVersion : clientFriends.getFriends()) {
            Friend myVersion = friendMap.get(clientVersion.getEmail());
            if (myVersion == null) {
                friendMap.put(clientVersion.getEmail(), clientVersion);
                result.shouldSave = true;
            } else if (myVersion.getLastUpdated() > clientVersion
                    .getLastUpdated()) {
                // my version has been updated more recently, so we will need
                // to sync it down to the client
                result.changed.add(myVersion);
                clientVersion.setLastUpdated(myVersion.getLastUpdated());
            } else if (myVersion.getLastUpdated() < clientVersion
                    .getLastUpdated()) {
                // the client version has been updated more recently, so
                // we need to save
                friendMap.put(clientVersion.getEmail(), clientVersion);
                result.shouldSave = true;
            }
        }

        if (result.shouldSave) {
            friends.clear();
            for (Friend friend : friendMap.values()) {
                friends.add(friend);
            }
            setFriends(friends);
        }

        // check for friends that the server knows about but the client doesn't
        if (clientFriends.getFriends().size() < friendMap.size()) {
            for (Friend friend : friendMap.values()) {
                if (clientFriends.get(friend.getEmail()) == null) {
                    result.changed.add(friend);
                }
            }
        }

        return result;
    }

    public List<Friend> getFriends() {
        ObjectMapper mapper = new ObjectMapper();
        if (StringUtils.isEmpty(getSerializedFriends())) {
            return new ArrayList<Friend>();
        }
        try {
            return mapper.readValue(getSerializedFriends(), new TypeReference<List<Friend>>(){});
        } catch (JsonParseException e) {
            throw new RuntimeException(e);
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setFriends(List<Friend> friends) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            setSerializedFriends(mapper.writeValueAsString(friends));
        } catch (JsonGenerationException e) {
            throw new RuntimeException(e);
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getSerializedFriends() {
        return serializedFriends;
    }

    public void setSerializedFriends(String serializedFriends) {
        this.serializedFriends = serializedFriends;
    }

    public boolean syncFriendFromClient(Friend clientFriend) {
        List<Friend> friends = getFriends();
        for (Friend friend : friends) {
            if (friend.getEmail().equals(clientFriend.getEmail())) {
                if (friend.getLastUpdated() <= clientFriend.getLastUpdated()) {
                    friend.update(clientFriend);
                    return true;
                } else {
                    return false;
                }
            }
        }
        friends.add(clientFriend);
        setFriends(friends);
        return true;
    }

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
}
