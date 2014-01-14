package org.lantern;

import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;
import org.lantern.loggly.LoggerFactory;


@SuppressWarnings("serial")
public class InviteRequestTaskHandler extends HttpServlet {

    private static final transient Logger log = LoggerFactory
            .getLogger(InviteRequestTaskHandler.class);

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        String[] senders = request.getParameter("senders").split(",");
        log.info("Got " + senders.length + " senders.");
        Dao dao = new Dao();
        for (String sender : senders) {
            dao.addInviteAndApproveIfUnpaused(
                    LanternControllerConstants.EMAIL_REQUEST_INVITER,
                    sender,
                    null);
        }
        LanternControllerUtils.populateOKResponse(response, "OK");
    }
}
