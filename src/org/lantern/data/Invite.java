package org.lantern.data;

import javax.persistence.Id;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Parent;

/**
 * An invite's id is inviter-email-address\1invitee-email-address
 */
public class Invite {

    @Id
    private String id;

    @Parent
    private Key<LanternUser> inviterKey;

    private String inviter;

    private String invitee;

    public enum Status {
        queued,
        sending,
        sent
    }

    private Status status = Status.queued;

    private long lastAttempt;

    public Invite() {
    };

    public Invite(String inviter, String invitee) {
        this.id = makeId(inviter, invitee);
        this.inviterKey = new Key<LanternUser>(LanternUser.class, inviter);
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
