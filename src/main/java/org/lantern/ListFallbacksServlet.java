package org.lantern;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.lantern.data.Dao;
import org.lantern.data.LanternInstance;

import com.googlecode.objectify.Objectify;

public class ListFallbacksServlet extends HttpServlet {
    private static final long serialVersionUID = -2328208258840617005L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Objectify ofy = new Dao().ofy();
        List<LanternInstance> instances = ofy.query(LanternInstance.class)
                .filter("isFallbackProxy", true).list();
        resp.setStatus(200);
        resp.setContentType("application/json");
        resp.getWriter().println("[");
        boolean first = true;
        for (LanternInstance instance : instances) {
            if (StringUtils.isEmpty(instance.getAccessData())) {
                continue;
            }
            if (instance.getNumberOfInvitesForFallback() <= 0) {
                continue;
            }

            if (!first) {
                resp.getWriter().println(", ");
            }
            first = false;
            resp.getWriter().print("    ");
            resp.getWriter().print(instance.getAccessData());
        }
        resp.getWriter().println("\n]");
    }
}
