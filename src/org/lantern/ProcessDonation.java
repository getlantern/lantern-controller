package org.lantern;

import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;


@SuppressWarnings("serial")
public class ProcessDonation extends HttpServlet {

    private static final transient Logger log = Logger
            .getLogger(ProcessDonation.class.getName());

    // In US dollar cents.
    private static int LAUNCH_UPFRONT_COST = 2000;

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        final String id = request.getParameter(
                LanternControllerConstants.DONATION_ID_KEY);
        final String email = request.getParameter(
                LanternControllerConstants.DONATION_EMAIL_KEY);
        final int amount = Integer.parseInt(request.getParameter(
                    LanternControllerConstants.DONATION_AMOUNT_KEY));
        log.info("Processing " + amount + "-cent donation " + id
                 + " from " + email);
        Dao dao = new Dao();
        int newBalance = dao.addCredit(email, amount);
        log.info(email + " has " + newBalance + " cents now.");
        if (newBalance >= LAUNCH_UPFRONT_COST) {
            log.info("That's enough to launch a proxy.");
        } else {
            log.info("That's not yet enough to launch a proxy.");
        }
        LanternControllerUtils.populateOKResponse(response, "OK");
    }
}
