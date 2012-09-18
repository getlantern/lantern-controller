package org.lantern;

import java.util.Collection;

import org.junit.Test;

public class MailChimpApiTest {

    @Test
    public void test() {
        final Collection<String> lists = MailChimpApi.lists();
        MailChimpApi.addSubscriber("lanternuser@gmail.com");
    }

}
