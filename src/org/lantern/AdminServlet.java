package org.lantern;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.lantern.data.Dao;

public class AdminServlet extends HttpServlet {
    private static final long serialVersionUID = -2328208258840617005L;
    private static final transient Logger log = Logger
            .getLogger(AdminServlet.class.getName());


    @Override
    public void doGet(final HttpServletRequest request,
            final HttpServletResponse response) throws ServletException, IOException {

        log.info("get on admin servlet; this should be handled by static files");
        response.setHeader("Location", "/admin/index.html");
        response.setStatus(302);
    }


    @Override
    public void doPost(final HttpServletRequest request,
            final HttpServletResponse response) {

        String path = request.getPathInfo();
        String[] pathComponents = StringUtils.split(path, "/");

        // URL is /admin/{command}[/...]
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
