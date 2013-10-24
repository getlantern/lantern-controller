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

import org.lantern.AdminServlet;

public class CSRFProtectionFilter implements Filter {
    private static final transient Logger log = Logger
            .getLogger(CSRFProtectionFilter.class.getName());

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    /**
     * See http://docs.angularjs.org/api/ng.$http#description_security-considerations_cross-site-request-forgery-protection
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String tokenExpected = "foo";//AdminServlet.getCsrfToken();
        if (req.getMethod().equalsIgnoreCase("post")) {
            String tokenReceived = req.getHeader("X-XSRF-TOKEN");
            if (!tokenExpected.equals(tokenReceived)) {
                log.info(String.format("Got bad anti-CSRF token: %1$s\nExpected: %2$s",
                        tokenReceived, tokenExpected));
                resp.setStatus(403);
                return;
            }
        }
        Cookie cookie = new Cookie("XSRF-TOKEN", tokenExpected);
        resp.addCookie(cookie);
        log.info(String.format("Cookie version: %1$s", cookie.getVersion()));
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
