package org.lantern.data;

import javax.persistence.Id;

/**
 * An invite's id is inviter-email-address\invitee-email-address
 */
public class Invite {

    @Id
    private final String id;

    public Invite(String inviter, String invitee) {
        this.id = makeKey(inviter, invitee);
    }

    public static String makeKey(String inviterEmail, String inviteeEmail) {
        return inviterEmail + "\0" + inviteeEmail;
    }

}
