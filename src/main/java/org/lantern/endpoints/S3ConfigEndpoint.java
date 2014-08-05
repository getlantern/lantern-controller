package org.lantern.endpoints;

import java.util.ArrayList;
import java.util.List;

import org.lantern.BaseS3Config;
import org.lantern.EmailAddressUtils;
import org.lantern.JsonUtils;
import org.lantern.data.Dao;
import org.lantern.data.Dao.DbCall;
import org.lantern.data.LanternInstance;
import org.lantern.data.LanternUser;
import org.lantern.proxy.FallbackProxy;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.utils.SystemProperty;
import com.googlecode.objectify.Objectify;

/**
 * Endpoint for interacting with the friends of a given user.
 */
@Api(name = "s3config",
        version = "v1",
        clientIds = { "323232879315-bea7ng41i8fsvua1takpcprbpd38nal9.apps.googleusercontent.com" },
        scopes = { "https://www.googleapis.com/auth/userinfo.email" })
public class S3ConfigEndpoint extends BaseEndpoint {

    /**
     * This method gets the logged in user's S3Config.
     * 
     * @return The logged-in user's S3Config (will have an empty collection of
     *         proxies for uninvited users)
     * @throws UnauthorizedException
     *             If the user is unauthorized.
     */
    @ApiMethod(
            httpMethod = "GET",
            name = "s3config.get",
            path = "s3config/get")
    public BaseS3Config get(
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        checkAuthorization(user);
        return new Dao().withObjectify(new DbCall<BaseS3Config>() {
            @Override
            public BaseS3Config call(Objectify ofy) {
                BaseS3Config config = new BaseS3Config();
                config.setController(SystemProperty.applicationId.get());
                String email = EmailAddressUtils.normalizedEmail(user
                        .getEmail());
                LanternUser lu = ofy.find(LanternUser.class, email);
                if (lu == null) {
                    log.info(String
                            .format("No user found for email: %s", email));
                    return config;
                }
                if (lu.getFallbackProxy() == null) {
                    log.info(String.format("No fallback for email: %s", email));
                    return config;
                }
                LanternInstance fallback = ofy.find(lu.getFallbackProxy());
                FallbackProxy fallbackProxy = JsonUtils.decode(
                        fallback.getAccessData(), FallbackProxy.class);
                List<FallbackProxy> fallbacks = new ArrayList<FallbackProxy>();
                fallbacks.add(fallbackProxy);
                config.setFallbacks(fallbacks);
                return config;
            }
        });
    }
}