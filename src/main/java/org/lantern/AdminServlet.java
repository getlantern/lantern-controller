package org.lantern;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;

import org.lantern.data.Dao;
import org.lantern.data.LanternUser;


public class AdminServlet extends HttpServlet {
    private static final long serialVersionUID = -2328208258840617005L;
    private static final transient Logger log = Logger
            .getLogger(AdminServlet.class.getName());
    private static String secret;
    
    static {
        secret = loadSecret();
    }

    private static String loadSecret() {
        if (StringUtils.isNotBlank(secret)) {
            return secret;
        }
        final Properties prop = new Properties();

        try {
            final ClassLoader cl = AdminServlet.class.getClassLoader();
            prop.load(cl.getResourceAsStream("csrf-secret.properties"));
            secret = prop.getProperty("secret");
            if (StringUtils.isBlank(secret)) {
                throw new RuntimeException(
                        "Please create a csrf-secret.properties file with field secret");
            }
            return secret;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NullPointerException e) {
            throw new RuntimeException("Failed to load CSRF secret", e);
        }
    }

    public static String getCsrfToken() {
        UserService userService = UserServiceFactory.getUserService();
        User user = userService.getCurrentUser();
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(),
                "HmacSHA256");

        Mac mac;
        try {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            // https://www.owasp.org/index.php/Cross-Site_Request_Forgery_(CSRF)_Prevention_Cheat_Sheet#Encrypted_Token_Pattern
            // not including timestamp and nonce because https-only admin pages prevents replay attacks
            // https://github.com/getlantern/lantern/issues/1077#issuecomment-27430617
            byte[] result = mac.doFinal(user.getEmail().getBytes());

            return Base64.encodeBase64String(result);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getCsrfTag() {
        return "<input type=\"hidden\" name=\"csrfToken\" value=\"" + getCsrfToken() + "\" />";
    }

    @Override
    public void doPost(final HttpServletRequest request,
            final HttpServletResponse response) {

        addCSPHeader(request, response);

        String tokenExpected = getCsrfToken();
        String tokenReceived = request.getParameter("csrfToken");
        if (!SecurityUtils.constantTimeEquals(tokenExpected, tokenReceived)) {
            log.info(String.format("invalid csrf token: %1$s", tokenReceived));
            return;
        }
        // TODO check token in header too?
        // https://www.owasp.org/index.php/Cross-Site_Request_Forgery_(CSRF)_Prevention_Cheat_Sheet#Double_Submit_Cookies

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

    public void setMaxInvitesPerProxy(
            final HttpServletRequest request,
            final HttpServletResponse response,
            String[] pathComponents) {
        try {
            Dao dao = new Dao();
            int n = Integer.parseInt(checkAndTrim(request, "n"));
            dao.setMaxInvitesPerProxy(n);
            LanternControllerUtils.populateOKResponse(
                    response,
                    "Set invites per proxy to: " + n);
        } catch (IOException e) {
            LanternControllerUtils.populateErrorResponse(
                    response, e.getMessage());
        }
    }

    public void promoteFallbackProxyUser(
            final HttpServletRequest request,
            final HttpServletResponse response,
            String[] pathComponents) {
        try {
            String userId = checkAndTrim(request, "user");
            Dao dao = new Dao();
            if (dao.findUser(userId) == null) {
                throw new IOException("no such user: " + userId);
            }
            if (isFallbackProxyUser(dao, userId)) {
                throw new IOException(
                        userId + " is already a fallback proxy user.");
            }
            dao.makeFallbackProxyUser(userId);
            LanternControllerUtils.populateOKResponse(
                    response,
                    "A proxy will run as " + userId
                    + " next time they invite someone.");
        } catch (IOException e) {
            LanternControllerUtils.populateErrorResponse(
                    response, e.getMessage());
        }
    }

    private boolean isFallbackProxyUser(Dao dao, String userId) {
        return userId.equals(dao.findUser(userId).getFallbackProxyUserId());
    }

    public void setInvitesPaused(final HttpServletRequest request,
            final HttpServletResponse response, String[] pathComponents) {

        Dao dao = new Dao();

        boolean paused = "true".equals(request.getParameter("paused"));

        dao.setInvitesPaused(paused);

        LanternControllerUtils.populateOKResponse(response, "Invites paused: " + paused);

    }

    public void sendUpdateEmail(HttpServletRequest request,
                                HttpServletResponse response,
                                String[] pathComponents) {
        try {
            String version = checkAndTrim(request, "version");
            String to = checkAndTrim(request, "to");
            Dao dao = new Dao();
            if ("EVERYONE, AND I MEAN IT!".equals(to)) {
                log.info("Sending to all users.");
                for (LanternUser user : dao.getAllUsers()) {
                    enqueueUpdateEmail(user, version);
                }
                LanternControllerUtils.populateOKResponse(
                        response,
                        "Sending completed, all apparently OK.");
            } else {
                log.info("Sending only to " + to);
                enqueueUpdateEmail(dao.findUser(to), version);
                LanternControllerUtils.populateOKResponse(
                    response,
                    "Sent update email to " + to);
            }
        } catch (IOException e) {
            LanternControllerUtils.populateErrorResponse(
                    response, e.getMessage());
        }
    }

    private String enqueueUpdateEmail(LanternUser user, String version)
            throws IOException {
        log.info("Enqueuing update notification to " + user);
        Dao dao = new Dao();
        if (user == null) {
            throw new IOException("Unknown user");
        }
        String installerLocation
            = dao.findInstance(
                    user.getFallbackProxy()).getInstallerLocation();
        QueueFactory.getDefaultQueue().add(
            TaskOptions.Builder
               .withUrl("/send_update_task")
               .param("toEmail", user.getId())
               .param("version", version)
               .param("installerLocation", installerLocation));
        return null;
    }

    public static void setSecret(final String secret) {
        AdminServlet.secret = secret;
    }

    /**
     * Check that the request parameter is not blank, and return its value
     * trimmed.
     *
     * @throws IOException if the value is null or empty.
     */
    private static String checkAndTrim(HttpServletRequest request,
                                       String param)
            throws IOException{
        String raw = request.getParameter(param);
        if (StringUtils.isBlank(raw)) {
            throw new IOException(
                    "Parameter can't be null or empty: " + param);
        }
        return raw.trim();
    }
}
