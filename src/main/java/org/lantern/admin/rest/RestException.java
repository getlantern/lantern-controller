package org.lantern.admin.rest;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Special exception thrown from Rest services.
 */
public class RestException extends WebApplicationException {
    private static final long serialVersionUID = 1L;

    public RestException(String message) {
        super(Response.status(Response.Status.BAD_REQUEST)
                .entity(message).type(MediaType.TEXT_PLAIN).build());
    }
}
