package org.lantern.admin.rest;

import java.util.Collection;
import java.util.logging.Logger;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.lantern.FallbackProxyLauncher;
import org.lantern.data.Invite;
import org.lantern.data.PMF;

@Path("/invites")
@JsonIgnoreProperties(ignoreUnknown = true)
public class InviteResource {
    private final static Logger LOG = Logger.getLogger("InviteResource");

    @GET
    @Path("/pending")
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<Invite> query(@QueryParam("where") String where,
            @QueryParam("ordering") String ordering) {
        final PersistenceManager mgr = PMF.get().getPersistenceManager();
        final Query query = mgr.newQuery(Invite.class);
        String filter = "status == 'queued'";
        if (!StringUtils.isBlank(where)) {
            filter = String.format("%1$s && %2$s", filter, where);
        }
        query.setFilter(filter);
        if (!StringUtils.isBlank(ordering)) {
            query.setOrdering(ordering);
        }
        return (Collection<Invite>) query.execute();
    }

    @POST
    @Path("/approve")
    @Consumes(MediaType.APPLICATION_JSON)
    public void approve(String[] ids) {
        FallbackProxyLauncher.authorizeInvites(ids);
    }
}