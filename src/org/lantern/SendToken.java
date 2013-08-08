package org.lantern;

import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@SuppressWarnings("serial")
public class SendToken extends HttpServlet {

    private static final transient Logger log = Logger
            .getLogger(SendToken.class.getName());

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("feed-token-for", request.getParameter("user"));
        m.put("feed-refrtok", request.getParameter("token"));
        new SQSUtil().send(m);
        LanternControllerUtils.populateOKResponse(response, "OK");
    }
}
