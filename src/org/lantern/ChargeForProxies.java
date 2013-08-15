package org.lantern;

import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;

import org.lantern.data.Dao;
import org.lantern.data.LanternUser;


@SuppressWarnings("serial")
public class ChargeForProxies extends HttpServlet {

    private static final transient Logger log =
        Logger.getLogger(ChargeForProxies.class.getName());

    /**
     * We send a warning three days before actually collecting payments, at
     * which point it's practically determined which servers we'll be shutting
     * down.
     *
     * We actually do the shutting down one day after collecting payments, as
     * a simple way to make sure we're seeing post-collect data.
     */
    private static final int WARN_DAY = 1;
    private static final int COLLECT_DAY = 4;
    private static final int PRUNE_DAY = 5;

    /**
     * How many proxies we can afford with our budget.
     */
    private static final int MAX_SUBSIDIZED_PROXIES
        = Math.floor(LanternControllerConstants.PROXY_MONTHLY_BUDGET
                     / LanternControllerConstants.PROXY_MONTHLY_COST);

    @Override
    public void doGet(final HttpServletRequest request,
                      final HttpServletResponse response) {
        int day = new GregorianCalendar().get(GregorianCalendar.DAY_OF_MONTH);
        if (day == WARN_DAY) {
            log.info("It's warn day.");
            issueWarnings();
        } else if (day == COLLECT_DAY) {
            log.info("It's collect day.");
            collectPayments();
        } else if (day == PRUNE_DAY) {
            log.info("It's prune day.");
            pruneProxies();
        } else {
            log.info("It's nothing day.");
        }
        LanternControllerUtils.populateOKResponse(response, "OK");
    }

    private void issueWarnings() {
        Dao dao = new Dao();
        Queue q = QueueFactory.getDefaultQueue();
        for (UserCredit c : dao.getOverdueProxies(MONTHLY_COST)) {
            // Do the actual sending of messages in a task queue, to make sure
            // we don't time out.
            q.add(TaskOptions.Builder.withUrl("/send_credit_warning")
                    .param(LanternControllerConstants.EMAIL_KEY, c.userId));
        }
    }

    private void collectPayments() {
        Dao dao = new Dao();
        Queue q = QueueFactory.getDefaultQueue();
        for (UserCredit c : dao.getRunningProxies()) {
            // Do the actual update (and, perhaps sending of messages) in a
            // task queue, to make sure we don't time out.
            q.add(TaskOptions.Builder.withUrl("/charge_proxy")
                    .param(LanternControllerConstants.ID_KEY, c.userId));
        }
    }

    private void pruneProxies() {
        Dao dao = new Dao();
        Queue q = QueueFactory.getDefaultQueue();
        // We only fetch the ones after MAX_SUBSIDIZED_PROXIES because
        // collecting payments left the others in the right state.
        Iterable<UserCredit> overdue = dao.getOverdueProxies(
                MONTHLY_COST, MAX_SUBSIDIZED_PROXIES);
        for (UserCredit c : overdue) {
            // Do the actual shutdown in a task queue, to make sure we don't
            // time out.
            q.add(TaskOptions.Builder.withUrl("/shutdown_proxy")
                    .param(LanternControllerConstants.ID_KEY, c.userId));
        }
    }
}
