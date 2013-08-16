package org.lantern;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;
import org.lantern.data.LanternUser;


@SuppressWarnings("serial")
public class ProcessPayment extends HttpServlet {

    private static final transient Logger log = Logger
            .getLogger(ProcessPayment.class.getName());

    //XXX: For testing; disable when deploying to the real controller!
    /*@Override
    public void doGet(final HttpServletRequest request,
                      final HttpServletResponse response) {
        doPost(request, response);
    }*/

    private enum MessageToSend {LAUNCH_START,
                                RECEIVED,
                                INSUFFICIENT}

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        final String id = request.getParameter(
                LanternControllerConstants.ID_KEY);
        final String email = request.getParameter(
                LanternControllerConstants.EMAIL_KEY);
        final int amount = Integer.parseInt(request.getParameter(
                    LanternControllerConstants.AMOUNT_KEY));
        log.info("Processing " + amount + "-cent payment " + id
                 + " from " + email);
        Dao dao = new Dao();
        int newBalance = dao.addCredit(email, amount);
        log.info(email + " has " + newBalance + " cents now.");
        MessageToSend mts;
        if (newBalance >= LanternControllerConstants.LAUNCH_UPFRONT_COST) {
            if (dao.getUser(email) == null) {
                if (dao.getUser("lanterndonors@gmail.com") == null) {
                    dao.createUser("adamfisk@gmail.com",
                                   "lanterndonors@gmail.com");
                }
                dao.createUser("lanterndonors@gmail.com", email);
            }
            if (dao.getAndSetInstallerLocation(
                        email, LanternControllerConstants.FPS_PENDING_START)
                 == null) {
                String token = dao.getUser(email).getRefreshToken();
                if (token == null) {
                    // DRY warning: lantern_aws/salt/cloudmaster/cloudmaster.py
                    token = "<tokenless-donor>";
                }
                log.info("Launching server for " + email);
                InvitedServerLauncher.orderServerLaunch(email, token);
                dao.recordProxyLaunch(email);
                mts = MessageToSend.LAUNCH_START;
            } else {
                mts = MessageToSend.RECEIVED;
            }
        } else if (dao.getUserCredit(email).getIsProxyRunning()) {
            mts = MessageToSend.RECEIVED;
        } else {
            mts = MessageToSend.INSUFFICIENT;
        }
        try {
            switch (mts) {
                case LAUNCH_START:
                    MandrillEmailer.sendProxyLaunching(email);
                    break;
                case RECEIVED:
                    MandrillEmailer.sendPaymentReceived(
                            email, amount, newBalance);
                    break;
                case INSUFFICIENT:
                    MandrillEmailer.sendInsufficientBalance(
                            email, amount, newBalance);
                    break;
                default:
                    throw new RuntimeException(mts.toString());
            }
        } catch (final IOException e) {
            log.severe( "Error trying to send " + mts + " mail!\n" + e);
            // Don't rethrow since that would requeue this task, and
            // this is not severe enough to warrant that.
        }
        LanternControllerUtils.populateOKResponse(response, "OK");
    }
}
