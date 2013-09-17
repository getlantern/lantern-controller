package org.lantern.data;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Keeps statistics on a metric that changes over time.
 * </p>
 * 
 * <p>
 * This calculates a moving average over time by bucketing samples into buckets
 * whose duration is determined by {@link bucketDurationInMilliseconds} and
 * limited in # of buckets by {@link numberOfBucketsToKeep}.
 * </p>
 */
public class Metric {
    private long bucketDurationInMilliseconds;
    private long numberOfBucketsToKeep = 0;

    // Main stats
    private Double min;
    private Double max;
    private double movingAverage = 0;

    // Stuff that we track to calculate buckets
    private double totalInCurrentBucket = 0;
    private long numberOfSamplesInCurrentBucket = 0;
    private long lastSampledBucket = 0;
    private List<Double> buckets = new ArrayList<Double>();

    /**
     * @param bucketDurationInMilliseconds
     *            how big to make each bucket for calculating moving averages
     * @param numberOfBucketsToKeep
     *            the number of buckets to keep
     */
    public Metric(long bucketDurationInMilliseconds,
            long numberOfBucketsToKeep) {
        super();
        this.bucketDurationInMilliseconds = bucketDurationInMilliseconds;
        this.numberOfBucketsToKeep = numberOfBucketsToKeep;
    }

    public void sample(long timestamp, double value) {
        // Update min and max values
        min = min == null || value < min ? value : min;
        max = max == null || value > max ? value : max;

        // Maintain moving average
        long bucketSinceEpoch = (long) Math.floor(timestamp
                / bucketDurationInMilliseconds);
        if (lastSampledBucket > 0 && bucketSinceEpoch > lastSampledBucket) {
            // roll over bucket
            buckets.add(totalInCurrentBucket / numberOfSamplesInCurrentBucket);
            // keep # of buckets limited
            if (buckets.size() > numberOfBucketsToKeep) {
                buckets.remove(0);
            }

            double totalOverAllBuckets = 0;
            for (double bucket : buckets) {
                totalOverAllBuckets += bucket;
            }
            this.movingAverage = totalOverAllBuckets / buckets.size();

            // Reinitialize current bucket
            this.totalInCurrentBucket = 0;
            this.numberOfSamplesInCurrentBucket = 0;
        }

        // update current bucket
        totalInCurrentBucket += value;
        numberOfSamplesInCurrentBucket += 1;

        // Remember our most recent sample
        lastSampledBucket = bucketSinceEpoch;
    }

    /**
     * The smallest individual sample ever recorded.
     * 
     * @return
     */
    public Double getMin() {
        return min;
    }

    /**
     * The largest individual sample ever recorded.
     * 
     * @return
     */
    public Double getMax() {
        return max;
    }

    /**
     * The moving average over the buckets limited to
     * {@link #numberOfBucketsToKeep}.
     * 
     * @return
     */
    public double getMovingAverage() {
        return movingAverage;
    }
}
