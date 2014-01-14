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
import org.lantern.data.LatestLanternVersion;
import org.lantern.loggly.LoggerFactory;


@Path("/LatestLanternVersion")
@JsonIgnoreProperties(ignoreUnknown = true)
public class LatestLanternVersionResource {
    private final static Logger LOG = LoggerFactory.getLogger("LatestLanternVersionResource");

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public LatestLanternVersion read() {
        Dao dao = new Dao();
        return dao.getLatestLanternVersion();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public void update(LatestLanternVersion lanternVersion) throws Exception {
        Dao dao = new Dao();
        dao.setLatestLanternVersion(lanternVersion);
    }

}
