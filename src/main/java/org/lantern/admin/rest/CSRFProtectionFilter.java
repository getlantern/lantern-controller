package org.lantern.admin.rest;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static final Pattern SAFE_METHODS = Pattern.compile("^(GET|HEAD|OPTIONS|TRACE)$");
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
        String method = req.getMethod();
        Matcher matcher = SAFE_METHODS.matcher(method);
        boolean requestAuthorized = true;
        String tokenExpected = AdminServlet.getCsrfToken();
        if (!matcher.matches()) {
            // belt... (http://blog.chromium.org/2010/01/security-in-depth-new-security-features.html)
            final String origin = req.getHeader("Origin");
            if (!StringUtils.equalsIgnoreCase(origin,
                        LanternControllerConstants.BASE_URL)) {
                log.warning(String.format("Invalid Origin: %1$s", origin));
                requestAuthorized = false;
            }
            // ...and suspenders (https://docs.djangoproject.com/en/1.2/releases/1.2.5/#django-1-2-5-release-notes)
            if (requestAuthorized) {
                String tokenReceived = req.getHeader("X-XSRF-TOKEN");
                String tokenReceivedWithoutQuotes = StringUtils.strip(tokenReceived, "\"");
                boolean matches = SecurityUtils.constantTimeEquals(tokenExpected, tokenReceived);
                boolean matchesWithoutQuotes = SecurityUtils.constantTimeEquals(tokenExpected, tokenReceivedWithoutQuotes);
                if (!matches & !matchesWithoutQuotes) { // & rather than && so as to not short-circuit
                    log.warning(String.format("Got bad CSRF token: %1$s", tokenReceived));
                    requestAuthorized = false;
                }
            }
        }
        HttpServletResponse resp = (HttpServletResponse) response;
        if (requestAuthorized) {
            Cookie cookie = new Cookie("XSRF-TOKEN", tokenExpected);
            cookie.setPath("/admin");
            boolean runningOnGAE = SystemProperty.environment.value() == SystemProperty.Environment.Value.Production;
            cookie.setSecure(runningOnGAE);
            resp.addCookie(cookie);
            chain.doFilter(request, response);
        } else {
            resp.setStatus(403);
        }
    }

    @Override
    public void destroy() {
    }
}
