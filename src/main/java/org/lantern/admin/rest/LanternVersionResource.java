package org.lantern.admin.rest;

import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.lantern.data.Dao;
import org.lantern.data.LanternVersion;


@Path("/LanternVersion/latest")
@JsonIgnoreProperties(ignoreUnknown = true)
public class LanternVersionResource {
    private final static Logger LOG = Logger.getLogger("LanternVersionResource");

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public LanternVersion read() {
        Dao dao = new Dao();
        return dao.getLatestLanternVersion();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public void update(LanternVersion lanternVersion) throws Exception {
        Dao dao = new Dao();
        dao.setLatestLanternVersion(lanternVersion);
    }

}
