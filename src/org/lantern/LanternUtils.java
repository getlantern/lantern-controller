package org.lantern;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.codehaus.jackson.map.SerializationConfig.Feature;

import com.google.appengine.api.xmpp.Message;

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
    
    /*
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
    */


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
    
    public static String jsonify(final Object all) {
        
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(Feature.INDENT_OUTPUT, true);
        //mapper.configure(Feature.SORT_PROPERTIES_ALPHABETICALLY, false);

        try {
            return mapper.writeValueAsString(all);
        } catch (final JsonGenerationException e) {
            System.out.println("Error generating JSON" + e);
        } catch (final JsonMappingException e) {
            System.out.println("Error generating JSON" + e);
        } catch (final IOException e) {
            System.out.println("Error generating JSON" + e);
        }
        return "";
    }
    
    public static String jsonify(final Object all, Class<?> view) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(Feature.INDENT_OUTPUT, true);
        ObjectWriter writer = mapper.writerWithView(view);
        try {
            return writer.writeValueAsString(all);
        } catch (final JsonGenerationException e) {
            System.out.println("Error generating JSON "+e);
        } catch (final JsonMappingException e) {
            System.out.println("Error generating JSON "+ e);
        } catch (final IOException e) {
            System.out.println("Error generating JSON " + e);
        }
        return "";
    }
}


