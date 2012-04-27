package org.lantern;

import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.Presence;

/**
 * Utility methods for the controller.
 */
public class LanternControllerUtils {

    /**
     * Returns whether or not the given ID is a lantern ID.
     * 
     * @param id The ID to check.
     * @return <code>true</code> if it's a Lantern ID, otherwise 
     * <code>false</code>.
     */
    public static boolean isLantern(final String id) {
        return id.contains("/-lan");
    }
    
    public static String userId(final Message message) {
        return LanternUtils.jidToUserId(message.getFromJid().getId());
    }
    
    public static String instanceId(final Message message) {
        return message.getFromJid().getId().split("/")[1];
    }
    
    public static String userId(final Presence presence) {
        return LanternUtils.jidToUserId(presence.getFromJid().getId());
    }

}
