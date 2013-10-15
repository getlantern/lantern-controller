package org.lantern.admin.rest;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

/**
 * <p>
 * Big thanks to this project for providing a template on using Jersey with
 * AppEngine.
 * </p>
 * 
 * <p>
 * https://github.com/GoogleCloudPlatform/appengine-angular-guestbook-java
 * </p>
 */
public class LanternAdminApplication extends Application {
    private Set<Class<?>> resources = new HashSet<Class<?>>();

    public LanternAdminApplication() {
        resources.add(LanternVersionResource.class);

        // register Jackson ObjectMapper resolver
        resources.add(CustomObjectMapperProvider.class);
    }

    @Override
    public Set<Class<?>> getClasses() {
        return resources;
    }
}