package org.lantern.endpoints;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Named;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.mrbean.MrBeanModule;
import org.lantern.JsonUtils;
import org.lantern.LanternConstants;
import org.lantern.data.Dao;
import org.lantern.state.Friend;
import org.lantern.state.Friends;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.ForbiddenException;
import com.google.appengine.api.oauth.OAuthRequestException;
import com.google.appengine.api.users.User;

/**
 * Defines v1 of the friends synchronization API.
 */
@Api(
    name = "friends",
    version = "v1",
    clientIds = {
        "323232879315-bea7ng41i8fsvua1takpcprbpd38nal9.apps.googleusercontent.com"
    }
)
public class FriendsEndpoint {
    
    private final transient Logger log = Logger.getLogger(getClass().getName());
    
    @ApiMethod(name = "friends.authed", httpMethod = "post", path = "friends",
            scopes = {"https://www.googleapis.com/auth/userinfo.email"})
    public JsonStringContainer authedFriends(final User user, @Named("friendsJson") 
        final String json) throws OAuthRequestException, ForbiddenException {
        if (user == null) {
            log.warning("User not authorized - null user object!");
            throw new OAuthRequestException("User not authorized");
        } 
        
        final Dao dao = new Dao();
        final String email = user.getEmail();
        if (!dao.isInvited(email)) {
            log.info("User not invited: "+email);
            throw new ForbiddenException("User not invited");
        }
        final String updated = handleFriendsSync(json, email);
        return new JsonStringContainer(updated);
    }
    

    private String handleFriendsSync(final String friendsJson, 
            final String userId) {
        log.info("Handling friend sync");
        final Dao dao = new Dao();

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new MrBeanModule());


        final Friends clientFriends = safeMap(friendsJson, mapper, Friends.class);

        log.info("Synced friends count = " + clientFriends.getFriends().size());

        final List<Friend> changed = dao.syncFriends(userId, clientFriends);
        log.info("Changed friends count = " + changed.size());
        if (changed.size() > 0) {
            final Map<String, Object> response = new HashMap<String, Object>();
            response.put(LanternConstants.FRIENDS, changed);
            final String json = JsonUtils.jsonify(response);
            return json;
        }

        return "";
    }
    
    private static final class JsonStringContainer {
        private String json;

        public JsonStringContainer(final String json) {
            this.json = json;
        }

        public String getJson() {
            return json;
        }

        public void setJson(String json) {
            this.json = json;
        }
    }

    private <T> T safeMap(final String json, final ObjectMapper mapper, 
            final Class<T> cls) {
        try {
            return mapper.readValue(json, cls);
        } catch (final UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (final JsonParseException e) {
            log.severe("Error parsing client message: " + e.getMessage());
            throw new RuntimeException(e);
        } catch (final JsonMappingException e) {
            log.severe("Error parsing client message: " + e.getMessage());
            throw new RuntimeException(e);
        } catch (IOException e) {
            log.severe("Error reading client message: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}