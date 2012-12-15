package org.lantern.data;


@SuppressWarnings("serial")
public class AlreadyInvitedException extends Exception {
    public AlreadyInvitedException() {
        super();
    }
    public AlreadyInvitedException(final String msg) {
        super(msg);
    }
}
