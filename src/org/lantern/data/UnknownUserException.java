package org.lantern.data;


@SuppressWarnings("serial")
public class UnknownUserException extends RuntimeException {
    public UnknownUserException() {
        super();
    }
    public UnknownUserException(final String msg) {
        super(msg);
    }
}
