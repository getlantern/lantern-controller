package org.lantern;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;
import org.lantern.data.Dao.DbCall;
import org.lantern.data.FallbackProxy;

import com.googlecode.objectify.Objectify;

import org.lantern.loggly.LoggerFactory;


@SuppressWarnings("serial")
public class LaunchFallbackSuccessorsTask extends HttpServlet {

    private static final transient Logger log = LoggerFactory
            .getLogger(LaunchFallbackSuccessorsTask.class);

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        launchNewProxyPair(request.getParameter("fallbackId"));
    }

    /**
     * Request a new pair of fallback proxies to be launched.
     *
     * parentId is the proxy that is being split.
     */
    private static void launchNewProxyPair(String parentId) {
        String family = new Dao().ofy().find(FallbackProxy.class, parentId).getFamily();
        for (int i=0; i<2; i++) {
            String fallbackId = createProxy(parentId, family, i);
            Map<String, Object> map = new HashMap<String, Object>();
            /*
             * This isn't in LanternConstants because they are not handled
             * by the client, but by a Python bot.
             * (salt/cloudmaster/cloudmaster.py)
             */
            map.put("launch-id", fallbackId);
            new SQSUtil().send(map);
        }
    }

    /**
     * Create a fallback proxy entry in the datastore.
     *
     * The serial number is a hint; a new one may be created in the unlikely
     * event that another proxy with the same one already exists.
     */
    private static String createProxy(final String parentId,
                                      final String family,
                                      final int serialNo) {
        final String date = new SimpleDateFormat("yyyy-MM-dd").format(
                Calendar.getInstance().getTime());
        Dao dao = new Dao();
        return dao.withTransaction(new DbCall<String>() {
            @Override
            public String call(Objectify ofy) {
                for (int serial = serialNo;
                     /* run until return */;
                     serial += 2 /* maintain parity */) {
                    String id = family + "-" + date + "-" + serial;
                    FallbackProxy fp = ofy.find(FallbackProxy.class, id);
                    if (fp != null) {
                        continue;
                    }
                    ofy.put(new FallbackProxy(id, parentId, family));
                    return id;
                }
            }
        });
    }
}
