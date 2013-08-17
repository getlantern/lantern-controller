package org.lantern;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;
import org.lantern.data.LanternUser;


@SuppressWarnings("serial")
public class ChargeProxy extends HttpServlet {

    private static final transient Logger log
        = Logger.getLogger(ChargeProxy.class.getName());

    //XXX: For testing; disable when deploying to the real controller!
    /*@Override
    public void doGet(final HttpServletRequest request,
                      final HttpServletResponse response) {
        doPost(request, response);
    }*/

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        Dao dao = new Dao();
        final String userId = request.getParameter(
                LanternControllerConstants.ID_KEY);
        final int centsCharged = dao.chargeProxy(userId);
        final int newBalance = dao.getUserCredit(userId).getBalance();
        // Don't send an email if the server was launched so recently that we
        // aren't charging anything this month.
        if (centsCharged > 0) {
            try {
                MandrillEmailer.sendProxyCharged(userId, centsCharged,
                                                 newBalance);
            } catch (final IOException e) {
                // Don't rethrow this error since this task will be rescheduled
                // unless we return 200 OK, and missing this notification is
                // preferrable to charging the user multiple times.
                log.severe("Couldn't send notification email: " + e);
            }
        }
        LanternControllerUtils.populateOKResponse(response, "OK");
    }
}
