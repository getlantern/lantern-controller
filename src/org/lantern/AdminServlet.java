package org.lantern;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.logging.Logger;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.lantern.data.Dao;

import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;

public class AdminServlet extends HttpServlet {
    private static final long serialVersionUID = -2328208258840617005L;
    private static final transient Logger log = Logger
            .getLogger(AdminServlet.class.getName());
    private static String secret;

    static {
        final Properties prop = new Properties();

        try {
            final ClassLoader cl = AdminServlet.class.getClassLoader();
            prop.load(cl.getResourceAsStream("csrf-secret.properties"));
            secret = prop.getProperty("secret");
            if (StringUtils.isBlank(secret)) {
                throw new RuntimeException(
                        "Please create a csrf-secret.properties file with field secret");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getCsrfToken() {
        UserService userService = UserServiceFactory.getUserService();
        User user = userService.getCurrentUser();
        return DigestUtils.sha256Hex(user.getFederatedIdentity() + secret);
    }

    public static String getCsrfTag() {
        return "<input type=\"hidden\" name=\"csrfToken\" value=\"" + getCsrfToken() + "\" />";
    }

    @Override
    public void doPost(final HttpServletRequest request,
            final HttpServletResponse response) {

        addCSPHeader(request, response);

        String csrfToken = getCsrfToken();
        if (!SecurityUtils.constantTimeEquals(csrfToken, request.getParameter("csrfToken"))) {
            return;
        }

        String path = request.getPathInfo();
        String[] pathComponents = StringUtils.split(path, "/");

        // URL is /admin/post/{command}[/...]
        String command = pathComponents[0];

        log.info("Admin command: " + command);
        try {
            //call the appropriate method on this class
            Method method = getClass().getMethod(command, HttpServletRequest.class, HttpServletResponse.class, String[].class);
            method.invoke(this, request, response, pathComponents);
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add content-security-policy header
     *
     * @param req
     * @param resp
     */
    public static void addCSPHeader(ServletRequest req,
            HttpServletResponse resp) {
        String policy = "default-src http://" + req.getServerName()
                + " 'unsafe-inline' 'unsafe-eval'";
        resp.addHeader("Content-Security-Policy", policy);
    }

    /**
     * Test action to make sure admin commands work
     */
    public void test(final HttpServletRequest request,
            final HttpServletResponse response, String[] pathComponents) {

        LanternControllerUtils.populateOKResponse(response, "Test: " + StringUtils.join(pathComponents, ","));
    }

    public void addInvites(final HttpServletRequest request,
            final HttpServletResponse response, String[] pathComponents) {

        Dao dao = new Dao();

        int n = Integer.parseInt(request.getParameter("n"));

        dao.globalAddInvites(n);

        LanternControllerUtils.populateOKResponse(response, "Invites added: " + n);
    }

    public void setInvitesPaused(final HttpServletRequest request,
            final HttpServletResponse response, String[] pathComponents) {

        Dao dao = new Dao();

        boolean paused = "true".equals(request.getParameter("paused"));

        dao.setInvitesPaused(paused);

        LanternControllerUtils.populateOKResponse(response, "Invites paused: " + paused);

    }

    public void setDefaultInvites(final HttpServletRequest request,
            final HttpServletResponse response, String[] pathComponents) {

        Dao dao = new Dao();


        int n = Integer.parseInt(request.getParameter("n"));

        dao.setDefaultInvites(n);

        LanternControllerUtils.populateOKResponse(response, "Default invites: " + n);

    }
}
