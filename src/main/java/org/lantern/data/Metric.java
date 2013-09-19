package org.lantern.data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Embedded;

import com.googlecode.objectify.annotation.Unindexed;

/**
 * <p>
 * Keeps statistics on a metric that changes over time.
 * </p>
 * 
 * <p>
 * This calculates a moving average over time by bucketing samples into periods
 * whose duration is determined by {@link #periodDurationInMilliseconds} and
 * limited in # of periods by {@link #numberOfPeriodsToKeep}.
 * </p>
 */
@Unindexed
public class Metric {
    private long periodDurationInMilliseconds;
    private long numberOfPeriodsToKeep = 0;

    // Main stats
    private Double min;
    private Double max;
    private double mostRecent = 0;
    private double movingAverageForAllPeriods = 0;
    private double movingAverageForCompletePeriods = 0;

    // Periods of stats
    @Embedded
    private Period currentPeriod;

    @Embedded
    private List<Period> periods = new ArrayList<Period>();

    public Metric() {
    }

    /**
     * @param periodDurationInMilliseconds
     *            how big to make each period for calculating moving averages
     * @param numberOfPeriodsToKeep
     *            the number of periods to keep
     */
    public Metric(long periodDurationInMilliseconds,
            long numberOfPeriodsToKeep) {
        super();
        this.periodDurationInMilliseconds = periodDurationInMilliseconds;
        this.numberOfPeriodsToKeep = numberOfPeriodsToKeep;
    }

    /**
     * Add a sample to this metric.
     * 
     * @param timestamp
     * @param value
     */
    public void addSample(long timestamp, double value) {
        // Update global values
        mostRecent = value;
        min = min == null || value < min ? value : min;
        max = max == null || value > max ? value : max;

        boolean periodAdvanced = advanceCurrentPeriodIfNecessary(timestamp);

        // Update the current period
        currentPeriod.addSample(value);

        // Update our moving averages if necessary
        if (periodAdvanced) {
            calculateMovingAverages();
        }
    }

    /**
     * Advances the {@link #currentPeriod} to the given timestamp. If the period
     * already covers that timestamp, this doesn't do anything.
     * 
     * @param timestamp
     * @return true if the current period was advanced
     */
    private boolean advanceCurrentPeriodIfNecessary(long timestamp) {
        long periodsSinceEpoch = (long) Math.floor(timestamp
                / periodDurationInMilliseconds);
        boolean advancedCurrentPeriod = false;
        while (currentPeriod == null
                || currentPeriod.offsetFromEpoch < periodsSinceEpoch) {
            currentPeriod = new Period(timestamp,
                    currentPeriod == null ? periodsSinceEpoch
                            : currentPeriod.offsetFromEpoch + 1);
            periods.add(currentPeriod);
            // Keep # of periods limited
            if (periods.size() > numberOfPeriodsToKeep) {
                periods.remove(0);
            }
            advancedCurrentPeriod = true;
        }
        return advancedCurrentPeriod;
    }

    private void calculateMovingAverages() {
        int numberOfPeriods = periods.size();
        double totalOverAllPeriods = 0;
        double totalOverCompletePeriods = 0;
        for (int i = 0; i < numberOfPeriods; i++) {
            Period period = periods.get(i);
            boolean isLast = i == numberOfPeriods - 1;
            totalOverAllPeriods += period.movingAverage;
            if (!isLast) {
                totalOverCompletePeriods += period.movingAverage;
            }
        }

        movingAverageForAllPeriods = totalOverAllPeriods
                / ((double) numberOfPeriods);
        movingAverageForCompletePeriods = totalOverCompletePeriods
                / ((double) numberOfPeriods - 1);
    }

    /**
     * The most recent sample.
     * 
     * @return
     */
    public double getMostRecent() {
        return mostRecent;
    }

    public void setMostRecent(double mostRecent) {
        this.mostRecent = mostRecent;
    }

    /**
     * The smallest individual sample ever recorded.
     * 
     * @return
     */
    public Double getMin() {
        return min;
    }

    public void setMin(Double min) {
        this.min = min;
    }

    /**
     * The largest individual sample ever recorded.
     * 
     * @return
     */
    public Double getMax() {
        return max;
    }

    public void setMax(Double max) {
        this.max = max;
    }

    /**
     * The moving average over all {@link #periods} including the
     * {@link #currentPeriod}.
     * 
     * @return
     */
    public double getMovingAverageForAllPeriods() {
        return movingAverageForAllPeriods;
    }

    public void setMovingAverageForAllPeriods(double movingAverageForAllPeriods) {
        this.movingAverageForAllPeriods = movingAverageForAllPeriods;
    }

    /**
     * The moving average over complete {@link #periods} excluding the
     * {@link #currentPeriod}.
     * 
     * @return
     */
    public double getMovingAverageForCompletePeriods() {
        return movingAverageForCompletePeriods;
    }

    public void setMovingAverageForCompletePeriods(
            double movingAverageForCompletePeriods) {
        this.movingAverageForCompletePeriods = movingAverageForCompletePeriods;
    }

    public long getPeriodDurationInMilliseconds() {
        return periodDurationInMilliseconds;
    }

    public void setPeriodDurationInMilliseconds(
            long periodDurationInMilliseconds) {
        this.periodDurationInMilliseconds = periodDurationInMilliseconds;
    }

    public long getNumberOfPeriodsToKeep() {
        return numberOfPeriodsToKeep;
    }

    public void setNumberOfPeriodsToKeep(long numberOfPeriodsToKeep) {
        this.numberOfPeriodsToKeep = numberOfPeriodsToKeep;
    }

    public Period getCurrentPeriod() {
        return currentPeriod;
    }

    public void setCurrentPeriod(Period currentPeriod) {
        this.currentPeriod = currentPeriod;
    }

    public List<Period> getPeriods() {
        return periods;
    }

    public void setPeriods(List<Period> periods) {
        this.periods = periods;
    }

    /**
     * Represents a period within this metric. Periods hold a running total and
     * associated information for a period defined by
     * {@link Metric#periodDurationInMilliseconds}.
     */
    public static class Period {
        private long startTime;
        private long offsetFromEpoch;
        private long numberOfSamples;
        private double runningTotal;
        private Double min;
        private Double max;
        private double movingAverage;

        public Period() {
        }

        public Period(long startTime, long offsetFromEpoch) {
            this.startTime = startTime;
            this.offsetFromEpoch = offsetFromEpoch;
        }

        public void addSample(double value) {
            // Update running total
            this.numberOfSamples += 1;
            this.runningTotal += value;

            // Update min and max values
            min = min == null || value < min ? value : min;
            max = max == null || value > max ? value : max;
            this.movingAverage = this.runningTotal / this.numberOfSamples;
        }

        public boolean hasStartTime() {
            return startTime > 0;
        }
        
        public Date getStartTime() {
            return new Date(startTime);
        }

        public long getOffsetFromEpoch() {
            return offsetFromEpoch;
        }

        public void setOffsetFromEpoch(long offsetFromEpoch) {
            this.offsetFromEpoch = offsetFromEpoch;
        }

        public long getNumberOfSamples() {
            return numberOfSamples;
        }

        public void setNumberOfSamples(long numberOfSamples) {
            this.numberOfSamples = numberOfSamples;
        }

        public double getRunningTotal() {
            return runningTotal;
        }

        public void setRunningTotal(double runningTotal) {
            this.runningTotal = runningTotal;
        }

        public Double getMin() {
            return min;
        }

        public void setMin(Double min) {
            this.min = min;
        }

        public Double getMax() {
            return max;
        }

        public void setMax(Double max) {
            this.max = max;
        }

        public double getMovingAverage() {
            return movingAverage;
        }

        public void setMovingAverage(double movingAverage) {
            this.movingAverage = movingAverage;
        }

    }

}
