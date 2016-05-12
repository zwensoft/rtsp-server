package com.sengled.cloud.http.metric;

import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Counter;
import com.codahale.metrics.CsvReporter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;

public class MetricsGraphicsReporter extends ScheduledReporter {

    /**
     * Returns a new {@link Builder} for {@link CsvReporter}.
     *
     * @param registry the registry to report
     * @return a {@link Builder} instance for a {@link CsvReporter}
     */
    public static Builder forRegistry(MetricRegistry registry) {
        return new Builder(registry);
    }

    /**
     * A builder for {@link CsvReporter} instances. Defaults to using the default locale, converting
     * rates to events/second, converting durations to milliseconds, and not filtering metrics.
     */
    public static class Builder {
        private final MetricRegistry registry;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private MetricFilter filter;

        private Builder(MetricRegistry registry) {
            this.registry = registry;
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.filter = MetricFilter.ALL;
        }

        

        /**
         * Convert rates to the given time unit.
         *
         * @param rateUnit a unit of time
         * @return {@code this}
         */
        public Builder convertRatesTo(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }

        /**
         * Convert durations to the given time unit.
         *
         * @param durationUnit a unit of time
         * @return {@code this}
         */
        public Builder convertDurationsTo(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }

        /**
         * Only report metrics which match the given filter.
         *
         * @param filter a {@link MetricFilter}
         * @return {@code this}
         */
        public Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Builds a {@link CsvReporter} with the given properties, writing {@code .csv} files to the
         * given directory.
         *
         * @param directory the directory in which the {@code .csv} files will be created
         * @return a {@link CsvReporter}
         */
        public MetricsGraphicsReporter build(MetricsGraphics metricsGraphics) {
            return new MetricsGraphicsReporter(registry,
                    metricsGraphics,
                    rateUnit,
                    durationUnit,
                    filter);
        }
    }

    private MetricsGraphics metricsGraphics;

    private MetricsGraphicsReporter(MetricRegistry registry,MetricsGraphics metricsGraphics,
            TimeUnit rateUnit, TimeUnit durationUnit, MetricFilter filter) {
        super(registry, "http-reporter", filter, rateUnit, durationUnit);
        
        this.metricsGraphics = metricsGraphics;
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges,
                       SortedMap<String, Counter> counters,
                       SortedMap<String, Histogram> histograms,
                       SortedMap<String, Meter> meters,
                       SortedMap<String, Timer> timers) {
        for (Entry<String, Gauge> entry : gauges.entrySet()) {
            logGauge(entry.getKey(), entry.getValue());
        }

        for (Entry<String, Counter> entry : counters.entrySet()) {
            logCounter(entry.getKey(), entry.getValue());
        }

        for (Entry<String, Histogram> entry : histograms.entrySet()) {
            logHistogram(entry.getKey(), entry.getValue());
        }

        for (Entry<String, Meter> entry : meters.entrySet()) {
            logMeter(entry.getKey(), entry.getValue());
        }

        for (Entry<String, Timer> entry : timers.entrySet()) {
            logTimer(entry.getKey(), entry.getValue());
        }
    }

    private void logTimer(String name,
                          Timer timer) {
        final Snapshot snapshot = timer.getSnapshot();

        getTable(
                prefix(name),
                "TIMER",
                "count, min, max, mean, stddev, median, p75, p95, p98, p99, p999, mean_rate, m1, m5, m15, rate_unit, duration_unit")
                .addRow(
                        timer.getCount(),
                        convertDuration(snapshot.getMin()),
                        convertDuration(snapshot.getMax()),
                        convertDuration(snapshot.getMean()),
                        convertDuration(snapshot.getStdDev()),
                        convertDuration(snapshot.getMedian()),
                        convertDuration(snapshot.get75thPercentile()),
                        convertDuration(snapshot.get95thPercentile()),
                        convertDuration(snapshot.get98thPercentile()),
                        convertDuration(snapshot.get99thPercentile()),
                        convertDuration(snapshot.get999thPercentile()),
                        convertRate(timer.getMeanRate()),
                        convertRate(timer.getOneMinuteRate()),
                        convertRate(timer.getFiveMinuteRate()),
                        convertRate(timer.getFifteenMinuteRate()),
                        getRateUnit(),
                        getDurationUnit());
    }

    private void logMeter(String name,
                          Meter meter) {
        getTable(prefix(name),
                "METER",
                "count, mean_rate, m1, m5, m15, rate_unit")
                .addRow(
                        meter.getCount(),
                        convertRate(meter.getMeanRate()),
                        convertRate(meter.getOneMinuteRate()),
                        convertRate(meter.getFiveMinuteRate()),
                        convertRate(meter.getFifteenMinuteRate()),
                        getRateUnit());
    }

    private void logHistogram(String name,
                              Histogram histogram) {
        final Snapshot snapshot = histogram.getSnapshot();
        getTable(
                prefix(name),
                "HISTOGRAM",
                "count, min, max, mean, stddev, median, p75, p95, p98, p99, p999")
                .addRow(
                        histogram.getCount(),
                        snapshot.getMin(),
                        snapshot.getMax(),
                        snapshot.getMean(),
                        snapshot.getStdDev(),
                        snapshot.getMedian(),
                        snapshot.get75thPercentile(),
                        snapshot.get95thPercentile(),
                        snapshot.get98thPercentile(),
                        snapshot.get99thPercentile(),
                        snapshot.get999thPercentile());
    }

    private void logCounter(String name,
                            Counter counter) {
        getTable(prefix(name), "COUNTER", "count").addRow(counter.getCount());
    }

    private void logGauge(String name,
                          Gauge gauge) {
        getTable(prefix(name), "GAUGE", "value").addRow(gauge.getValue());
    }

    private String prefix(String name) {
        return name;
    }

    private Table getTable(String name,
                           String type,
                           String colTemplates) {
        return metricsGraphics.getOrCreateTable(name,
                type,
                colTemplates);
    }
}
