package org.lantern;

import java.util.ArrayList;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.lantern.data.Dao;
import org.lantern.loggly.LoggerFactory;


@SuppressWarnings("serial")
public class PortUsersTask extends ExtendedJob {

    private static final int NUM_FALLBACKS = 100;

    public static final String PATH = "/port_users_task";

    private static final transient Logger log = LoggerFactory
            .getLogger(PortUsersTask.class);

    @Override
    protected void start(HttpServletRequest request,
                         ArrayList<String> args) {
        log.info("Starting...");
        int numUsers = args.size();
        for (int i=0; i<numUsers; i++) {
            args.set(i, fpuid(i%NUM_FALLBACKS) + " " + args.get(i));
        }
        doContinue(request, args);
    }

    private String fpuid(int i) {
        return "from-old-controller-"+i+"@getlantern.org";
    }

    @Override
    protected void processOneArg(String arg) {
        log.info("Processing: '" + arg + "'");
        String[] parts = arg.split(" ");
        String inviter = EmailAddressUtils.normalizedEmail(parts[0]);
        String invitee = EmailAddressUtils.normalizedEmail(parts[1]);
        new Dao().addInviteAndApproveIfUnpaused(
                inviter, invitee, "new-trust-network-invite");
        log.info(invitee + " invited.");
    }
}
