package org.lantern.data;

import javax.persistence.Id;

/**
 * An invite's id is inviter-email-address\1invitee-email-address
 */
public class Invite {

    @Id
    private String id;

    public Invite() {
    };

    public Invite(String inviter, String invitee) {
        this.id = makeKey(inviter, invitee);
    }

    public static String makeKey(String inviterEmail, String inviteeEmail) {
        return inviterEmail + "\1" + inviteeEmail;
    }

}
