package org.lantern;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.MessageType;

import org.littleshoot.util.ThreadUtils;

import org.lantern.data.AlreadyInvitedException;
import org.lantern.data.UnknownUserException;
import org.lantern.data.Dao;

public class InvitedServerLauncher {

    private static final transient Logger log = 
        Logger.getLogger(InvitedServerLauncher.class.getName());

    public static final String LAUNCHING = "launching";
    public static final String INVSRVLAUNCHER_EMAIL = "invsrvlauncher@gmail.com";
    private static final JID INVSRVLAUNCHER_JID = new JID(INVSRVLAUNCHER_EMAIL);

    public static void onInvite(final String inviterName,
                           final String inviterEmail, 
                           final String refreshToken, 
                           final String invitedEmail) {

        final Dao dao = new Dao();
        try {
            dao.addInvite(inviterEmail, invitedEmail);
        } catch (final AlreadyInvitedException e) {
            log.info(inviterEmail 
                     + " had already invited " 
                     + invitedEmail);
            return;
        } 
        
        String invitedServer = dao.getAndSetInvitedServer(inviterEmail);
        if (invitedServer == null && refreshToken != null) {
            // Ask invsrvlauncher to create an instance for this user.
            log.info("Ordering launch of new invitedServer for " 
                     + inviterEmail);
            final XMPPService xmpp = XMPPServiceFactory.getXMPPService();
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            /* These aren't in LanternConstants because they are not handled
             * by the client, but by a Python XMPP bot.
             * (salt/invsrvlauncher/xmpp-bot.py at lantern_aws, branch
             * invsrvlauncher)
             */
            map.put("launch-invsrv-as", inviterEmail);
            map.put("launch-refrtok", refreshToken);
            final String body = LanternUtils.jsonify(map);
            Message msg = new MessageBuilder()
                .withMessageType(MessageType.HEADLINE)
                .withRecipientJids(INVSRVLAUNCHER_JID)
                .withBody(body)
                .build();
            xmpp.sendMessage(msg);

        } else if (!invitedServer.equals(LAUNCHING)) {
            sendInvite(inviterName, inviterEmail, invitedEmail, invitedServer);
        }
    }

    public static void onInvitedServerUp(final String inviterEmail, 
                                         final String address) {
        final Dao dao = new Dao();
        try {
            final Collection<String> invitees = dao.setInvitedServerAndGetInvitees(inviterEmail, address);
            for (String invitedEmail : invitees) {
                sendInvite(inviterEmail, inviterEmail, invitedEmail, address);
            }
        } catch (final UnknownUserException e) {
            log.severe("Server up for unknown inviter " + inviterEmail);
        }
    }

    private static void sendInvite(final String inviterName, final String inviterEmail, final String invitedEmail,
                            final String invitedServer) {
        try {
            MandrillEmailer.sendInvite(inviterName, inviterEmail, invitedEmail, invitedServer);
        } catch (final IOException e) {
            log.warning("Could not send e-mail!\n"+ThreadUtils.dumpStack());
        }
    }
}
