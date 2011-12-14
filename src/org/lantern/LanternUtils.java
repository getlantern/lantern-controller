package org.lantern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;

import com.google.appengine.api.xmpp.Message;
import com.google.appengine.repackaged.org.json.JSONArray;
import com.google.appengine.repackaged.org.json.JSONException;

/**
 * Utility methods for use with Lantern.
 */
public class LanternUtils {
    
    public static Collection<String> getCountryCodes() {
        Locale[] locales = Locale.getAvailableLocales();
        final Set<String> codes = new TreeSet<String>();
        for (Locale locale : locales) {
            //String iso = locale.getISO3Country();
            String code = locale.getCountry();
            //String name = locale.getDisplayCountry();

            if (StringUtils.isNotBlank(code)) {
                //System.out.println(iso);
                codes.add("\""+code+"\"");
                //System.out.println("\""+code+"\",");
            }
            //if (!"".equals(iso) && !"".equals(code) && !"".equals(name)) {
                //countries.add(new Country(iso, code, name));
            //}
        }
        return codes;
        //return new LinkedList<String>();
    }

    public static void main (final String[] args) {
        System.out.println(getCountryCodes());
    }
    
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


