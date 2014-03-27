package org.lantern;

import static org.junit.Assert.*;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;

public class EmailAddressUtilsTest {
    @Test
    public void testNormalizedEmail() throws Exception {
        String[] inputs = {
                "abc@gmail.com",
                "AbC@GMaiL.COM",
                "a.bc@gmail.com",
                "a.b.c@gmail.com",
                "abc+123@gmail.com",
                "a.b.c+123@gmail.com",
                "abc@googlemail.com",
                "A.b.C+123@googlemail.com"
        };
        String outputExpected = "abc@gmail.com";
        for (String input : inputs) {
            String outputObserved = EmailAddressUtils.normalizedEmail(input);
            assertTrue(StringUtils.equals(outputObserved, outputExpected));
        }

        String googleAppsAddr = "abc@googleappsdomain.net";
        String transformed = EmailAddressUtils.normalizedEmail(googleAppsAddr);
        assertTrue(StringUtils.equals(transformed, googleAppsAddr));
        
        assertNull(EmailAddressUtils.normalizedEmail(null));
        assertEquals("", EmailAddressUtils.normalizedEmail(""));
    }
}
