package org.lantern.admin.rest;

import java.util.Date;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.lantern.data.Dao;
import org.lantern.data.LanternVersion;


@Path("/LanternVersion/{id}")
@JsonIgnoreProperties(ignoreUnknown = true)
public class LanternVersionResource {
    private final static Logger LOG = Logger.getLogger("LanternVersionResource");

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public void createOrUpdate(LanternVersion lanternVersion) throws Exception {
        Dao dao = new Dao();
        dao.createOrUpdateLanternVersion(lanternVersion);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public LanternVersion read(@PathParam("id") String id) {
        return new LanternVersion(id, "a1b2c3", new Date(), null,
            "https://github.com/getlantern/lantern/releases/1.0.0-RC1");
    }
}
