package com.cloudsim;

import com.cloudsim.monitor.MetricsLogger;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class MetricsLoggerTest {

    @Test
    void testMetricsLoggerRuns() {

        MetricsLogger.SchedulerResult result =
                new MetricsLogger.SchedulerResult();

        result.totalCost = 100;
        result.avgCpuUtil = 50;
        result.avgRamUtil = 60;
        result.makespan = 200;

        assertNotNull(result);
    }
    @Test
    void testMetricsLoggerFile() throws Exception {
        MetricsLogger.SchedulerResult sr = new MetricsLogger.SchedulerResult();
        sr.totalCost = 10;

        MetricsLogger.writeTaskMetrics(
                "target/test_metrics.csv",
                new ArrayList<>(),
                sr
        );

        assertTrue(new File("target/test_metrics.csv").exists());
    }
}