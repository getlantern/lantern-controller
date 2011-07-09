package org.lantern;

import java.util.ArrayList;
import java.util.Collection;

import com.google.appengine.api.xmpp.Message;
import com.google.appengine.repackaged.org.json.JSONArray;
import com.google.appengine.repackaged.org.json.JSONException;

/**
 * Utility methods for use with Lantern.
 */
public class LanternUtils {

    public static Collection<String> toCollection(final JSONArray json) {
        final int length = json.length();
        final Collection<String> strs = new ArrayList<String>(length);
        for (int i = 0; i < length; i++) {
            try {
                strs.add((String) json.get(i));
            } catch (final JSONException e) {
                e.printStackTrace();
            }
        }
        return strs;
    }


    public static String userId(final Message message) {
        return message.getFromJid().getId().split("/")[0];
    }
    
    public static String instanceId(final Message message) {
        return message.getFromJid().getId().split("/")[1];
    }

    public static String jidToUserId(final String fullId) {
        return fullId.split("/")[0];
    }
    
    public static String jidToInstanceId(final String fullId) {
        return fullId.split("/")[1];
    }
}


