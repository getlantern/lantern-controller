package org.lantern;

import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.apphosting.api.DeadlineExceededException;

import org.apache.commons.lang3.StringUtils;
import org.lantern.loggly.LoggerFactory;


/**
 * Convenience superclass for handlers for possibly long-running jobs.
 *
 * In order to work around GAE's limits on the duration of servlets and task
 * queue handlers, we catch DeadlineExceededExceptions and post a new task for
 * the work left to do.
 *
 * Typically, you'll want to
 *   - override PATH
 *   - override processOneArg,
 *   - possibly override start.
 */
@SuppressWarnings("serial")
public class ExtendedJob extends HttpServlet {

    private static final transient Logger log = LoggerFactory
            .getLogger(ExtendedJob.class);

    public static final String ARGS = "args";
    public static final String PATH = "OVERRIDE ME!";

    private static final String PHASE = "phase";
    private static final String START = "start";
    private static final String CONTINUE = "continue";
    protected static final String separator = "\n";

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        List<String> args = parseArgs(request);
        try {
            String phase = request.getParameter(PHASE);
            if (phase == null || START.equals(phase)) {
                start(request, args);
            } else if (CONTINUE.equals(phase)) {
                doContinue(request, args);
            } else {
                log.severe("Unrecognized phase: " + phase);
                populateOKResponse(response);
                return;
            }
            populateResponse(response);
        } catch (DeadlineExceededException e) {
            log.info("Got DEE; requeuing...");
            QueueFactory.getDefaultQueue().add(
                customizeTaskOptions(
                    TaskOptions.Builder.withUrl(PATH)
                                       .param(PHASE, CONTINUE)
                                       .param(ARGS, serializeArgs(args)),
                    request));
            populateOKResponse(response);
        }
    }

    protected interface ArgPusher {
        void pushArg(String s);
    }

    protected void processOneArg(String arg,
                                 ArgPusher argPusher) {
        throw new UnsupportedOperationException("Override me or doContinue!");
    }

    protected void start(HttpServletRequest request,
                         List<String> args) {
        doContinue(request, args);
    }

    protected List<String> parseArgs(HttpServletRequest request) {
        String rawArgs = request.getParameter(ARGS);
        if (rawArgs == null) {
            return new ArrayList<String>();
        } else {
            return Arrays.asList(rawArgs.trim().split(separator));
        }
    }

    protected String serializeArgs(List<String> args) {
        return StringUtils.join(args, separator);
    }

    protected void doContinue(final HttpServletRequest request,
                              final List<String> args) {
        ArgPusher argPusher = new ArgPusher() {
            public void pushArg(String arg) {
                args.add(arg);
            }
        };
        while (!args.isEmpty()) {
            processOneArg(args.get(0), argPusher);
            args.remove(0);
        }
        finalize(request);
    }

    protected void finalize(HttpServletRequest request) {
    }

    protected void populateOKResponse(HttpServletResponse response) {
        LanternControllerUtils.populateOKResponse(response, "OK");
    }

    protected void populateResponse(HttpServletResponse response) {
        populateOKResponse(response);
    }

    protected TaskOptions customizeTaskOptions(TaskOptions to,
                                               HttpServletRequest request) {
        return to;
    }
}
