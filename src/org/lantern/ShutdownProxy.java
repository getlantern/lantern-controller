package org.lantern;

import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;
import org.lantern.data.LanternUser;


@SuppressWarnings("serial")
public class ShutdownProxy extends HttpServlet {

    private static final transient Logger log
        = Logger.getLogger(ShutdownProxy.class.getName());

    // In US dollar cents.
    private static int LAUNCH_UPFRONT_COST = 2000;

    //XXX: For testing; disable when deploying to the real controller!
    @Override
    public void doGet(final HttpServletRequest request,
                      final HttpServletResponse response) {
        doPost(request, response);
    }

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        final String id = request.getParameter(
                LanternControllerConstants.ID_KEY);
        LanternControllerUtils.populateOKResponse(response, "OK");
    }
}
