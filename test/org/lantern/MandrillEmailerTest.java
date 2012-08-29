package org.lantern;

import static org.junit.Assert.assertEquals;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Test;

/**
 * Test for sending e-mails using the Mandrill API.
 */
public class MandrillEmailerTest {

    private final String inviterName = "";

    private final String inviterEmail = "";
    
    private final String invitedEmail = "";
    
    @Test
    public void test() throws Exception {
        if (StringUtils.isBlank(inviterName) ||
            StringUtils.isBlank(invitedEmail)) {
            // Ignore the test
            return;
        }
        final String payload = MandrillEmailer.mandrillJson(inviterName, 
            inviterEmail, invitedEmail);
        final HttpPost post = new HttpPost(
            LanternControllerConstants.MANDRILL_API_SEND_TEMPLATE_URL);
        post.setEntity(new StringEntity(payload, "UTF-8"));
        final DefaultHttpClient httpclient = new DefaultHttpClient();
        final HttpResponse response = httpclient.execute(post);
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

}
