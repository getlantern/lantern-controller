package org.lantern;

import java.util.logging.Logger;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;

import org.lantern.data.Dao;
import org.lantern.data.Dao.DbCall;
import org.lantern.data.FallbackProxy;
import org.lantern.data.LanternUser;
import org.lantern.loggly.LoggerFactory;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;


@SuppressWarnings("serial")
public class CheckFallbacksJob extends ExtendedJob {

    private static final transient Logger log = LoggerFactory
            .getLogger(CheckFallbacksJob.class);

    protected final String path = "/check_fallbacks_job";

    @Override
    protected void start(HttpServletRequest request,
                         List<String> args) {
        Dao dao = new Dao();
        for (Key<FallbackProxy> key
             : dao.ofy().query(FallbackProxy.class)
                        .filter("status", FallbackProxy.Status.active)
                        .fetchKeys()) {
            args.add(key.getName());
        }
        doContinue(request, args);
    }

    @Override
    protected void processOneArg(final String fallbackId,
    		                     final ArgPusher argPusher) {
        Dao dao = new Dao();
        int numUsers = dao.ofy().query(LanternUser.class)
                                .filter("fallback",
                                        Key.create(FallbackProxy.class,
                                                   fallbackId))
                                .count();
        log.info(fallbackId + " has " + numUsers + " users.");
        if (numUsers > dao.getMaxInvitesPerProxy()) {
            log.info("Fallback full: " + fallbackId);
            dao.withTransaction(new DbCall<Void>() {
                @Override
                public Void call(Objectify ofy) {
                    FallbackProxy fp = ofy.find(FallbackProxy.class, fallbackId);
                    if (fp.getStatus() == FallbackProxy.Status.active) {
                        fp.setStatus(FallbackProxy.Status.splitting);
                        ofy.put(fp);
                        QueueFactory.getDefaultQueue().add(
                            TaskOptions.Builder
                               .withUrl("/launch_fallback_successors_task")
                               .param("fallbackId", fallbackId));
                    } else {
                        log.warning("Fallback in unexpected state: "
                                    + fp.getStatus());
                    }
                    return null;
                }
            });
        }
    }
}
