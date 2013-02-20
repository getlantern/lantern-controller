package org.lantern;

import java.io.StringReader;
import java.util.Iterator;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.xml.sax.InputSource;

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
        return getProperty(presence, LanternConstants.INVITEE_NAME);
    }

    public static String getProperty(final Presence presence,
        final String key) {
        try {
            StringReader reader = new StringReader(presence.getStanza());
            InputSource inputSource = new InputSource(reader);
            XPath xpath = XPathFactory.newInstance().newXPath();

            NamespaceContext ctx = new NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefix) {
                    String uri;
                    if (prefix.equals("ns1"))
                        uri = "http://www.jivesoftware.com/xmlns/xmpp/properties";
                    else if (prefix.equals("jabber:client"))
                        uri = "jabber:client";
                    else {
                        uri = null;
                        assert false : "Unexpected prefix";
                    }
                    return uri;
                }

                @Override
                public String getPrefix(String arg0) {
                    return null;
                }

                @Override
                public Iterator<?> getPrefixes(String arg0) {
                    return null;
                }

            };
            xpath.setNamespaceContext(ctx);
            String expression = "/jabber:client:presence/ns1:properties/ns1:property[ns1:name='"
                    + key + "']/ns1:value/text()";
            return xpath.evaluate(expression, inputSource);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
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
        return fullId.split("/", 2)[1];
    }

    public static String instanceId(Presence presence) {
        return jidToInstanceId(presence.getFromJid().getId());
    }
}
