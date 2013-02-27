package org.lantern.data;

import javax.persistence.Id;

/**
 * An invite's id is inviter-email-address\1invitee-email-address
 */
public class Invite {

    @Id
    private String id;

    private String inviter;

    private String invitee;

    public Invite() {
    };

    public Invite(String inviter, String invitee) {
        this.id = makeKey(inviter, invitee);
        this.inviter = inviter;
        this.invitee = invitee;
    }

    public static String makeKey(String inviterEmail, String inviteeEmail) {
        return inviterEmail + "\1" + inviteeEmail;
    }

    public String getInviter() {
        return inviter;
    }

    public String getInvitee() {
        return invitee;
    }


}
