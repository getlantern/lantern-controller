package org.lantern.admin.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.lantern.data.Dao;
import org.lantern.data.Invite;
import org.lantern.data.LanternUser;
import org.lantern.data.PMF;

@Path("/invites")
@JsonIgnoreProperties(ignoreUnknown = true)
public class InvitesResource {
    @GET
    @Path("/pending")
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<InviteWithUsers> query(@QueryParam("where") String where,
            @QueryParam("ordering") String ordering) {
        try {
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
            Collection<Invite> invites = (Collection<Invite>) query.execute();
            Set<String> ids = new HashSet<String>();
            for (Invite invite : invites) {
                ids.add(invite.getInvitee());
                ids.add(invite.getInviter());
            }
            List<LanternUser> lanternUsers = new Dao().findUsersByIds(ids);
            Map<String, LanternUser> lanternUsersById = new HashMap<String, LanternUser>();
            for (LanternUser lanternUser : lanternUsers) {
                lanternUsersById.put(lanternUser.getId(), lanternUser);
            }
            List<InviteWithUsers> result = new ArrayList<InviteWithUsers>();
            for (Invite invite : invites) {
                LanternUser inviter = lanternUsersById.get(invite.getInviter());
                LanternUser invitee = lanternUsersById.get(invite.getInvitee());
                if (invitee == null) {
                    // Only include invitations for people who aren't already
                    // Lantern users
                    result.add(new InviteWithUsers(invite,
                            inviter,
                            invite.getInvitee()));
                }
            }
            return result;
        } catch (Exception e) {
            throw new RestException("Unable to search pending invites: "
                    + e.getMessage());
        }
    }

    @POST
    @Path("/authorize")
    @Consumes(MediaType.APPLICATION_JSON)
    public int authorize(String[] ids) {
        return FallbackProxyLauncher.authorizeInvites(ids);
    }

    @POST
    @Path("/delete")
    @Consumes(MediaType.APPLICATION_JSON)
    public int delete(String[] ids) {
        return new Dao().deletePendingInvites(ids);
    }

    public static class User {
        private String id;
        private Integer degree;
        private Boolean hasFallback;
        private Collection<String> countries;
        private String sponsor;

        public User(LanternUser lanternUser) {
            this.id = lanternUser.getId();
            this.degree = lanternUser.getDegree();
            this.hasFallback = lanternUser.getFallbackForNewInvitees() != null;
            this.countries = lanternUser.getCountryCodes();
            this.sponsor = lanternUser.getSponsor();
        }

        public User(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public Integer getDegree() {
            return degree;
        }

        public Boolean getHasFallback() {
            return hasFallback;
        }

        public Collection<String> getCountries() {
            return countries;
        }

        public String getSponsor() {
            return sponsor;
        }

    }

    public static class InviteWithUsers {
        private String id;
        private User inviter;
        private User invitee;

        public InviteWithUsers(Invite invite, LanternUser inviter,
                String inviteeId) {
            super();
            this.id = invite.getId();
            this.inviter = new User(inviter);
            this.invitee = new User(inviteeId);
        }

        public String getId() {
            return id;
        }

        public User getInviter() {
            return inviter;
        }

        public User getInvitee() {
            return invitee;
        }
    }
}