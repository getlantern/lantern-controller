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
import org.lantern.SecurityUtils;

/**
 * This filter makes sure that the admin/rest APIs have a CSRF token available
 * and that operations on these APIs that manipulate data (everything but GET
 * and HEAD) always check the CSRF token before processing the operation.
 */
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
        String tokenExpected = AdminServlet.getCsrfToken();
        Cookie cookie = new Cookie("XSRF-TOKEN", tokenExpected);
        cookie.setPath("/admin");
        cookie.setMaxAge(-1); // session cookie - expires when browser closes
        if (false) { // TODO: actually check for whether we're running in production here
            cookie.setSecure(true);
        }
        resp.addCookie(cookie);
        String method = req.getMethod();
        if (!method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("HEAD")) {
            String tokenReceived = req.getHeader("X-XSRF-TOKEN");
            String tokenReceivedWithoutQuotes = StringUtils.strip(tokenReceived, "\"");
            // don't short circuit the second string comparison to prevent timing attacks
            boolean matches = SecurityUtils.constantTimeEquals(tokenExpected, tokenReceived);
            boolean matchesWithoutQuotes = SecurityUtils.constantTimeEquals(tokenExpected, tokenReceivedWithoutQuotes);
            if (matches || matchesWithoutQuotes) {
                log.info("CSRF tokens match");
            } else {
                log.info(String.format("Got bad CSRF token: %1$s\nExpected: %2$s",
                        tokenReceived, tokenExpected)); // TODO: don't log tokenExpected
                cookie.setValue("invalid token");
                cookie.setMaxAge(0); // delete the cookie
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
