package org.lantern;

import org.apache.commons.lang.StringUtils;

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
        return jidToUserId(message.getFromJid().getId());
    }
    
    public static String invitedName(final Presence presence) {
        return getProperty(presence, LanternConstants.INVITE_NAME);
    }
    
    public static String getProperty(final Presence presence, 
        final String key) {
        final String start = 
            "<property><name>" + key + 
            "</name><value type=\"string\">";
        final String stanza = presence.getStanza();
        return StringUtils.substringBetween(stanza, start, 
            "</value></property>");
    }
    
    public static String instanceId(final Message message) {
        return message.getFromJid().getId().split("/")[1];
    }
    
    public static String userId(final Presence presence) {
        return jidToUserId(presence.getFromJid().getId());
    }
    
    public static String jidToUserId(final String fullId) {
        return fullId.split("/")[0];
    }
    
    public static String jidToInstanceId(final String fullId) {
        return fullId.split("/")[1];
    }
}
