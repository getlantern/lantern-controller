package org.lantern.admin.rest;

import java.util.Date;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.lantern.data.LanternVersion;
import org.lantern.data.SemanticVersion;

@Path("/lantern-version")
public class LanternVersionResource {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public LanternVersion doGet() {
        SemanticVersion version = new SemanticVersion(1, 0, 0, "RC1");
        return new LanternVersion(version, "a1b2c3", new Date(), null,
            "https://github.com/getlantern/lantern/releases/1.0.0-RC1");
    }
}
