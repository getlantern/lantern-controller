package org.lantern.admin.rest;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.lantern.data.FriendingQuota;
import org.lantern.friending.Friending;

@Path("/friendingquota")
@JsonIgnoreProperties(ignoreUnknown = true)
public class FriendingQuotaResource {
    private static final Logger LOGGER = Logger
            .getLogger(FriendingQuotaResource.class.getName());

    @GET
    @Path("/{email}")
    @Produces(MediaType.APPLICATION_JSON)
    public FriendingQuota getQuota(@PathParam("email") String email) {
        if (email != null) {
            email = email.trim();
        }
        if (email == null || email.trim().length() == 0) {
            LOGGER.log(Level.WARNING, "No email provided, returning null");
            return null;
        }
        try {
            return Friending.getQuota(email);
        } catch (Exception e) {
            String msg = "Unable to get quota: " + e.getMessage();
            LOGGER.log(Level.WARNING, msg, e);
            e.printStackTrace();
            throw new RestException(msg);
        }
    }

    @POST
    @Path("/{email}/maxAllowed")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setMaxAllowed(
            @PathParam("email") String email,
            int newMaxAllowed) {
        try {
            Friending.setMaxAllowed(email, newMaxAllowed);
        } catch (Exception e) {
            String msg = "Unable to increase quota: " + e.getMessage();
            LOGGER.log(Level.WARNING, msg, e);
            throw new RestException(msg);
        }
    }
}