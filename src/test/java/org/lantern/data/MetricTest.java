package org.lantern.data;

import static org.junit.Assert.*;

import org.junit.Test;

public class MetricTest {
    private static final long ONE_SECOND = 1000;
    private static final long ONE_MINUTE = 60 * ONE_SECOND;
    private static final long ONE_HOUR = 60 * ONE_MINUTE;

    @Test
    public void testAddAndSet() throws Exception {
        Metric metric = new Metric(ONE_HOUR, 2);
        // Start on the hour boundary
        long now = (long) Math.floor(System.currentTimeMillis() / ONE_HOUR)
                * ONE_HOUR;

        // Add a sample, which shouldn't show up in the moving average
        metric.sample(now, 1);
        assertDoubleEquals(1, metric.getMin());
        assertDoubleEquals(1, metric.getMax());
        assertDoubleEquals(0, metric.getMovingAverage());

        // Add a sample which also shouldn't show up in the moving average
        metric.sample(now + 59 * ONE_MINUTE, 5);
        assertDoubleEquals(1, metric.getMin());
        assertDoubleEquals(5, metric.getMax());
        assertDoubleEquals(0, metric.getMovingAverage());

        // Add a sample which causes us to roll over the first bucket
        metric.sample(now + 60 * ONE_MINUTE, 2);
        assertDoubleEquals(1, metric.getMin());
        assertDoubleEquals(5, metric.getMax());
        assertDoubleEquals(3, metric.getMovingAverage());

        // Add another sample in the current bucket
        metric.sample(now + 61 * ONE_MINUTE, 6);
        assertDoubleEquals(1, metric.getMin());
        assertDoubleEquals(6, metric.getMax());
        assertDoubleEquals(3, metric.getMovingAverage());

        // Add a sample which causes us to roll over the second bucket
        metric.sample(now + 120 * ONE_MINUTE, 10);
        assertDoubleEquals(1, metric.getMin());
        assertDoubleEquals(10, metric.getMax());
        assertDoubleEquals(3.5, metric.getMovingAverage());

        // Add a sample which causes us to roll over the third bucket and roll
        // off the first bucket
        metric.sample(now + 180 * ONE_MINUTE, 3);
        assertDoubleEquals(1, metric.getMin());
        assertDoubleEquals(10, metric.getMax());
        assertDoubleEquals(7, metric.getMovingAverage());
        
        // Add a sample which causes us to roll over two buckets
        metric.sample(now + 300 * ONE_MINUTE, 5);
        assertDoubleEquals(1.5, metric.getMovingAverage());
    }
    
    @Test
    public void testNegativeValue() throws Exception {
        Metric metric = new Metric(ONE_HOUR, 2);
        // Start on the hour boundary
        long now = (long) Math.floor(System.currentTimeMillis() / ONE_HOUR)
                * ONE_HOUR;

        // Add a sample, which shouldn't show up in the moving average
        metric.sample(now, -1);
        assertDoubleEquals(-1, metric.getMin());
        assertDoubleEquals(-1, metric.getMax());
        assertDoubleEquals(0, metric.getMovingAverage());
        
     // Add a sample, which shouldn't show up in the moving average
        metric.sample(now + 60 * ONE_MINUTE, -1);
        assertDoubleEquals(-1, metric.getMin());
        assertDoubleEquals(-1, metric.getMax());
        assertDoubleEquals(-1, metric.getMovingAverage());
    }

    /**
     * Compare equality to 3 significant digits.
     * 
     * @param expected
     * @param actual
     */
    private void assertDoubleEquals(double expected, double actual) {
        assertEquals((long) Math.floor(expected * 1000),
                (long) Math.floor(actual * 1000));
    }
}
