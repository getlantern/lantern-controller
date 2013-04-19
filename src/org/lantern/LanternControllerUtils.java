package org.lantern;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;

import javax.servlet.http.HttpServletResponse;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.Presence;

/**
 * Utility methods for the controller.
 */
public class LanternControllerUtils {

    private static class MyEntityResolver implements EntityResolver {
        @Override
        public InputSource resolveEntity(String publicId, String systemId) {
            //prevent xml remote entity attacks
            throw new RuntimeException("Only local entities allowed");
        }
    }
    static {
        //prevent xml entity expansion attacks
        System.setProperty("entityExpansionLimit", "100");
    }

    private static HashMap<String, XPathExpression> xPathCache = new HashMap<String, XPathExpression>();

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


    public static Document buildDoc(final Presence presence) {
        String stanza = presence.getStanza();
        if (stanza.length() > 10000) {
            //prevent xml generic entity expansion
            throw new RuntimeException("Unexpectedly long stanza");
        }
        final DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(true);
        DocumentBuilder builder;
        try {
            domFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            domFactory.setValidating(false);
            builder = domFactory.newDocumentBuilder();
            builder.setEntityResolver(new MyEntityResolver());
            byte[] bytes = stanza.getBytes();
            InputStream is = new ByteArrayInputStream(bytes);
            return builder.parse(is);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getProperty(final Document doc,
        final String key) {
        try {
            XPathExpression compiled = getXPathExpression(key);
            return compiled.evaluate(doc);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    private static XPathExpression getXPathExpression(final String key)
            throws XPathExpressionException {

        XPathExpression expression = xPathCache.get(key);
        if (expression != null) {
            return expression;
        }

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
                //required by interface but unused
                return null;
            }

            @Override
            public Iterator<?> getPrefixes(String arg0) {
                //required by interface but unused
                return null;
            }

        };
        xpath.setNamespaceContext(ctx);
        String expressionStr = "/jabber:client:presence/ns1:properties/ns1:property[ns1:name='"
                + key + "']/ns1:value/text()";
        XPathExpression compiled = xpath.compile(expressionStr);

        xPathCache.put(key, compiled);
        return compiled;
    }

    public static String userId(final Message message) {
        return LanternXmppUtils.jidToUserId(message.getFromJid().getId());
    }

    public static String userId(final Presence presence) {
        return LanternXmppUtils.jidToUserId(presence.getFromJid().getId());
    }

    public static String instanceId(final Message message) {
        return LanternXmppUtils.jidToInstanceId(message.getFromJid().getId());
    }

    public static String instanceId(Presence presence) {
        return LanternXmppUtils.jidToInstanceId(presence.getFromJid().getId());
    }

    public static String jabberIdFromUserAndResource(final String userId,
                                                     final String resource) {
        return userId + "/" + resource;
    }

    /**
     * Populate 'response' as a plain text successful response with the given
     * text.
     *
     * Also, turn any exceptions into `RuntimeException`s so we don't need
     * declare them in the calling method.
     */
    public static void populateOKResponse(HttpServletResponse response,
                                          String text) {
        response.setContentType("text/plain");
        response.setStatus(HttpServletResponse.SC_OK);
        byte[] content;
        try {
            content = text.getBytes("UTF-8");
            response.setContentLength(content.length);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        try {
            final OutputStream os = response.getOutputStream();
            os.write(content);
            os.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
