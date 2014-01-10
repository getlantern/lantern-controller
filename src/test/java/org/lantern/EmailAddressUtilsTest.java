package org.lantern;

import static org.junit.Assert.*;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;

import com.google.appengine.repackaged.org.apache.commons.io.IOUtils;

public class EmailAddressUtilsTest {
    @Test
    public void testNormalizedEmail() throws Exception {
        String[] inputs = {
                "abc@gmail.com",
                "AbC@gmail.com",
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
    }
}
