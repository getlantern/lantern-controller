package org.lantern;

import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import org.lantern.loggly.LoggerFactory;


@SuppressWarnings("serial")
public class LaunchFallbackSuccessorsTask extends HttpServlet {

    private static final transient Logger log = LoggerFactory
            .getLogger(LaunchFallbackSuccessorsTask.class);

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        String parentId = request.getParameter("fallbackId");
        log.info("Launching new fallbacks to replace " + parentId);
        for (int i=1; i<3; i++) {
            String fallbackId = FallbackProxyLauncher.createProxy(parentId, i);
            log.info("Requesting launch of fallback " + fallbackId);
            FallbackProxyLauncher.requestProxyLaunch(fallbackId);
        }
        LanternControllerUtils.populateOKResponse(response, "OK");
    }
}
