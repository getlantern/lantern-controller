package org.lantern.admin.rest;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.lantern.AdminServlet;
import org.lantern.LanternControllerConstants;
import org.lantern.SecurityUtils;

import com.google.appengine.api.utils.SystemProperty;

/**
 * This filter makes sure that operations on the admin/rest APIs that manipulate
 * data (everything but GET and HEAD) always check the Origin header before
 * processing the operation to prevent cross-site request forgery.
 */
public class CSRFProtectionFilter implements Filter {
    private static final transient Logger log = Logger
            .getLogger(CSRFProtectionFilter.class.getName());

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        boolean runningOnGAE = SystemProperty.environment.value() == SystemProperty.Environment.Value.Production;
        String method = req.getMethod();
        if (!method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("HEAD")) {
            final String origin = req.getHeader("Origin");
            if (runningOnGAE
                    && !StringUtils.equalsIgnoreCase(origin,
                            LanternControllerConstants.BASE_URL)) {
                log.warning(String.format("Invalid Origin: %1$s", origin));
                resp.setStatus(403);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
