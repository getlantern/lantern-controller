package org.lantern;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

import org.junit.Test;


public class MailHandlerServletTest {

    private void checkMatches(String text, String... expectedMatches) {
        Set<String> result = new HashSet<String>();
        MailHandlerServlet.extractSendersFromText(text, result);
        assertEquals(new HashSet<String>(Arrays.asList(expectedMatches)), result);
    }

    private void checkWholeMatch(String text) {
        checkMatches(text, text);
    }

    @Test
    public void testTwo() throws Exception {
        checkMatches("one@test.com two@test.com",
                     "one@test.com", "two@test.com");
        checkMatches("one@test.com\ntwo@test.com",
                     "one@test.com", "two@test.com");
    }

    @Test
    public void testInSingleQuotes() throws Exception {
        checkMatches("'user@test.com'", "user@test.com");
    }

    @Test
    public void testInDoubleQuotes() throws Exception {
        checkMatches("\"user@test.com\"", "user@test.com");
    }

    @Test
    public void testInBrackets() throws Exception {
        checkMatches("<user@test.com>", "user@test.com");
    }

    @Test
    public void testInMailto() throws Exception {
        checkMatches("mailto:user@test.com", "user@test.com");
    }

    @Test
    public void testModeratelyFunkyChars() throws Exception {
        checkWholeMatch("_user+plus.dot-dash@test.dot_under-dash.longtld");
    }
}
