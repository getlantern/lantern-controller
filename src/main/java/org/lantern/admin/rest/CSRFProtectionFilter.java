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
        // guilty until proven innocent
        Cookie cookie = new Cookie("XSRF-TOKEN", "invalid");
        cookie.setMaxAge(0); // delete the cookie
        cookie.setPath("/admin");
        boolean runningOnGAE = SystemProperty.environment.value() == SystemProperty.Environment.Value.Production;
        cookie.setSecure(runningOnGAE);
        resp.addCookie(cookie);
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
            String tokenReceived = req.getHeader("X-XSRF-TOKEN");
            String tokenReceivedWithoutQuotes = StringUtils.strip(tokenReceived, "\"");
            // don't short circuit the second string comparison to prevent timing attacks
            boolean matches = SecurityUtils.constantTimeEquals(tokenExpected, tokenReceived);
            boolean matchesWithoutQuotes = SecurityUtils.constantTimeEquals(tokenExpected, tokenReceivedWithoutQuotes);
            if ("lanternctrl".equals(SystemProperty.applicationId.get()) // XXX tokenReceived is null in test controllers; disabling this check until that gets fixed.
                && !matches && !matchesWithoutQuotes) {
                log.warning(String.format("Got bad CSRF token: %1$s", tokenReceived));
                resp.setStatus(403);
                return;
            }
        }
        cookie.setMaxAge(-1); // session cookie - expires when browser closes
        cookie.setValue(tokenExpected);
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
