package com.usthe.collector.collect.http.promethus.exporter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author tom
 * @date 2022/12/4 18:14
 */
class ExporterParserTest {

    @Test
    void textToMetric() {
        String resp = "# HELP go_gc_cycles_automatic_gc_cycles_total Count of completed GC cycles generated by the Go runtime.\n" +
                        "# TYPE go_gc_cycles_automatic_gc_cycles_total counter\n" +
                        "go_gc_cycles_automatic_gc_cycles_total 0\n" +
                        "# HELP go_gc_cycles_forced_gc_cycles_total Count of completed GC cycles forced by the application.\n" +
                        "# TYPE go_gc_cycles_forced_gc_cycles_total counter\n" +
                        "go_gc_cycles_forced_gc_cycles_total 0\n" +
                        "# HELP go_gc_cycles_total_gc_cycles_total Count of all completed GC cycles.\n" +
                        "# TYPE go_gc_cycles_total_gc_cycles_total counter\n" +
                        "go_gc_cycles_total_gc_cycles_total 0\n" +
                        "# HELP go_gc_duration_seconds A summary of the pause duration of garbage collection cycles.\n" +
                        "# TYPE go_gc_duration_seconds summary\n" +
                        "go_gc_duration_seconds{quantile=\"0\"} 0\n" +
                        "go_gc_duration_seconds{quantile=\"0.25\"} 0\n" +
                        "go_gc_duration_seconds{quantile=\"0.5\"} 0\n" +
                        "go_gc_duration_seconds{quantile=\"0.75\"} 0\n" +
                        "go_gc_duration_seconds{quantile=\"1\"} 0\n" +
                        "# TYPE jvm info\n" +
                        "# HELP jvm VM version info\n" +
                        "jvm_info{runtime=\"OpenJDK Runtime Environment\",vendor=\"Azul Systems, Inc.\",version=\"11.0.13+8-LTS\"} 1.0\n" +
                        "# TYPE jvm_gc_collection_seconds summary\n" +
                        "# HELP jvm_gc_collection_seconds Time spent in a given JVM garbage collector in seconds.\n" +
                        "jvm_gc_collection_seconds_count{gc=\"G1 Young Generation\"} 10.0\n" +
                        "jvm_gc_collection_seconds_sum{gc=\"G1 Young Generation\"} 0.051\n" +
                        "jvm_gc_collection_seconds_count{gc=\"G1 Old Generation\"} 0.0\n" +
                        "jvm_gc_collection_seconds_sum{gc=\"G1 Old Generation\"} 0.0\n" +
                        "# EOF";
        ExporterParser parser = new ExporterParser();
        Map<String, MetricFamily> metricFamilyMap = parser.textToMetric(resp);
        assertEquals(metricFamilyMap.size(), 6);
    }
}