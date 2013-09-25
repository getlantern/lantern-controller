package org.lantern;

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.appengine.repackaged.org.apache.commons.io.IOUtils;

public class LibratoTest {
    @Test
    public void testGoodPath() throws Exception {
        String json = IOUtils.toString(LibratoTest.class
                .getResourceAsStream("/librato_sample_data.json"));
        String sourceName = "proxy-429e523560d0f39949843833f05c808e";
        double count = Librato.getMaxFromSummarizedMetric(
                json, sourceName);
        assertEquals(1, (int) count);
    }
    
    @Test
    public void testBadPath() throws Exception {
        String json = IOUtils.toString(LibratoTest.class
                .getResourceAsStream("/librato_sample_data.json"));
        String sourceName = "proxy-429e523560d0f39949843833f05c808f";
        double count = Librato.getMaxFromSummarizedMetric(
                json, sourceName);
        assertEquals(0, (int) count);
    }
}
