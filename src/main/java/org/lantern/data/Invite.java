package org.lantern.data;

import javax.persistence.Id;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Parent;

/**
 * Datastore model for an invite request.
 *
 * We store all invite requests ever, for the following purposes:
 *  - to keep track of the requests that are pending admin approval,
 *  - to prevent an invite from the same inviter to the same invitee from ever
 *    being processed more than once, and in particular
 *  - to avoid sending the corresponding invite e-mail more than once.
 */
public class Invite {

    @Id
    // Determined by both inviter and invitee; see makeId.
    private String id;

    @Parent
    // TRANSITION: this points to the inviter's fallbackProxyUserId if they
    // have a non-null one.  Otherwise (or for old invites created before the
    // fallback-balancing scheme was deployed) this points to the inviter.
    private Key<LanternUser> inviterKey;

    private String inviter;

    private String invitee;

    // Status transitions only ever advance monotonically in the order in which
    // they are listed below.
    public enum Status {
        // This invite hasn't been authorized yet.
        queued,
        // Invite has been authorized; we have never tried to send the e-mail
        // for this invite.
        authorized,
        // We are trying to send the e-mail for this invite, but we don't think
        // it's done yet.
        sending,
        // We believe we have succeeded in sending the e-mail for this invite.
        sent
    }

    private Status status = Status.queued;

    private long lastAttempt;

    public Invite() {
    };

    public Invite(String inviter, String invitee, String parent) {
        this.id = makeId(inviter, invitee);
        this.inviterKey = new Key<LanternUser>(LanternUser.class, parent);
        this.inviter = inviter;
        this.invitee = invitee;
    }

    public static String makeId(String inviterEmail, String inviteeEmail) {
        return inviterEmail + "\1" + inviteeEmail;
    }

    public String getInviter() {
        return inviter;
    }

    public String getInvitee() {
        return invitee;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public long getLastAttempt() {
        return lastAttempt;
    }

    public void setLastAttempt(long lastAttempt) {
        this.lastAttempt = lastAttempt;
    }

}
