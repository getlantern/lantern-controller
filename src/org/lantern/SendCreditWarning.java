package org.lantern;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;


@SuppressWarnings("serial")
public class SendCreditWarning extends HttpServlet {

    private static final transient Logger log
        = Logger.getLogger(SendCreditWarning.class.getName());

    //XXX: For testing; disable when deploying to the real controller!
    /*@Override
    public void doGet(final HttpServletRequest request,
                      final HttpServletResponse response) {
        doPost(request, response);
    }*/

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        final String email = request.getParameter(
                LanternControllerConstants.EMAIL_KEY);
        int balance = new Dao().getUserCredit(email).getBalance();
        // Might as well recheck that balance is too low.  We may have possibly
        // received a new payment since this was queued.  While it's not
        // terrible if a warning e-mail is unduly sent due to such a race
        // condition, it would be embarassing if the balance reported in that
        // email is above the monthly cost.
        if (balance < LanternControllerConstants.PROXY_MONTHLY_COST) {
            log.info("Sending credit warning to " + email
                     + " with balance " + balance);
            try {
                MandrillEmailer.sendCreditWarning(email, balance);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            log.info(
                "Nevermind, user must have added funds since I got queued.");
        }
        LanternControllerUtils.populateOKResponse(response, "OK");
    }
}
