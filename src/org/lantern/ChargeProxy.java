package org.lantern;

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
    @Override
    public void doGet(final HttpServletRequest request,
                      final HttpServletResponse response) {
        doPost(request, response);
    }

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        new Dao().chargeProxy(
                request.getParameter(LanternControllerConstants.ID_KEY),
                LanternControllerConstants.PROXY_MONTHLY_COST);
        LanternControllerUtils.populateOKResponse(response, "OK");
    }
}
