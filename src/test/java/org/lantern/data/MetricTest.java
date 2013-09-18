package org.lantern.data;

import static org.junit.Assert.*;

import org.junit.Test;

public class MetricTest {
    private static final long ONE_SECOND = 1000;
    private static final long ONE_MINUTE = 60 * ONE_SECOND;
    private static final long ONE_HOUR = 60 * ONE_MINUTE;

    @Test
    public void testAddAndSet() throws Exception {
        Metric metric = new Metric(ONE_HOUR, 3);
        // Start on the hour boundary
        long now = (long) Math.floor(System.currentTimeMillis() / ONE_HOUR)
                * ONE_HOUR;
        
        // Make sure that initial values are as expected
        assertDoubleEquals(0, metric.getMostRecent());
        assertDoubleEquals(0, metric.getMovingAverageForCompletePeriods());
        assertDoubleEquals(0, metric.getMovingAverageForAllPeriods());

        // Add a sample, which shouldn't show up in the moving average
        metric.addSample(now, 1);
        assertDoubleEquals(1, metric.getMostRecent());
        assertDoubleEquals(1, metric.getMin());
        assertDoubleEquals(1, metric.getMax());
        assertDoubleEquals(0, metric.getMovingAverageForCompletePeriods());

        // Add a sample which also shouldn't show up in the moving average
        metric.addSample(now + 59 * ONE_MINUTE, 5);
        assertDoubleEquals(5, metric.getMostRecent());
        assertDoubleEquals(1, metric.getMin());
        assertDoubleEquals(5, metric.getMax());
        assertDoubleEquals(0, metric.getMovingAverageForCompletePeriods());

        // Add a sample which causes us to roll over the first period
        metric.addSample(now + 60 * ONE_MINUTE, 2);
        assertDoubleEquals(2, metric.getMostRecent());
        assertDoubleEquals(1, metric.getMin());
        assertDoubleEquals(5, metric.getMax());
        assertDoubleEquals(3, metric.getMovingAverageForCompletePeriods());

        // Add another sample in the current period
        metric.addSample(now + 61 * ONE_MINUTE, 6);
        assertDoubleEquals(6, metric.getMostRecent());
        assertDoubleEquals(1, metric.getMin());
        assertDoubleEquals(6, metric.getMax());
        assertDoubleEquals(3, metric.getMovingAverageForCompletePeriods());

        // Add a sample which causes us to roll over the second period
        metric.addSample(now + 120 * ONE_MINUTE, 10);
        assertDoubleEquals(10, metric.getMostRecent());
        assertDoubleEquals(1, metric.getMin());
        assertDoubleEquals(10, metric.getMax());
        assertDoubleEquals(3.5, metric.getMovingAverageForCompletePeriods());

        // Add a sample which causes us to roll over the third period and roll
        // off the first period
        metric.addSample(now + 180 * ONE_MINUTE, 3);
        assertDoubleEquals(3, metric.getMostRecent());
        assertDoubleEquals(1, metric.getMin());
        assertDoubleEquals(10, metric.getMax());
        assertDoubleEquals(7, metric.getMovingAverageForCompletePeriods());

        // Add a sample which causes us to roll over two periods
        metric.addSample(now + 300 * ONE_MINUTE, 5);
        assertDoubleEquals(5, metric.getMostRecent());
        assertDoubleEquals(1.5, metric.getMovingAverageForCompletePeriods());
    }

    @Test
    public void testNegativeValue() throws Exception {
        Metric metric = new Metric(ONE_HOUR, 2);
        // Start on the hour boundary
        long now = (long) Math.floor(System.currentTimeMillis() / ONE_HOUR)
                * ONE_HOUR;

        // Add a sample, which shouldn't show up in the moving average
        metric.addSample(now, -1);
        assertDoubleEquals(-1, metric.getMin());
        assertDoubleEquals(-1, metric.getMax());
        assertDoubleEquals(0, metric.getMovingAverageForCompletePeriods());

        // Add a sample, which shouldn't show up in the moving average
        metric.addSample(now + 60 * ONE_MINUTE, -1);
        assertDoubleEquals(-1, metric.getMin());
        assertDoubleEquals(-1, metric.getMax());
        assertDoubleEquals(-1, metric.getMovingAverageForCompletePeriods());
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
