package org.lantern;

import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@SuppressWarnings("serial")
public class ProcessDonation extends HttpServlet {

    private static final transient Logger log = Logger
            .getLogger(ProcessDonation.class.getName());

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        final String idKey = LanternControllerConstants.DONATION_ID_KEY;
        final String emailKey = LanternControllerConstants.DONATION_EMAIL_KEY;
        final String amountKey
            = LanternControllerConstants.DONATION_AMOUNT_KEY;
        StringBuilder s = new StringBuilder();
        s.append("id: ");
        s.append(request.getParameter(idKey));
        s.append(", email: ");
        s.append(request.getParameter(emailKey));
        s.append(", cents: ");
        s.append(request.getParameter(amountKey));
        log.info("Got donation: " + s.toString());
        LanternControllerUtils.populateOKResponse(response, "OK");
    }
}
