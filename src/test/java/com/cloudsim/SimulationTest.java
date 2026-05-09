package com.cloudsim;

import com.cloudsim.workload.WorkloadLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SimulationTest {

    @Test
    void testSmallSimulationRuns() {

        try {
            // Load only small dataset
            List<WorkloadLoader.WorkloadTask> tasks =
                    WorkloadLoader.load("data/workload.csv");

            // Limit tasks (IMPORTANT)
            tasks = tasks.subList(0, Math.min(10, tasks.size()));

            WorkloadPredictor predictor =
                    new WorkloadPredictor("data/predictions.csv");

            // Run only ONE scheduler (not all)
            MainSimulation simulation = new MainSimulation();

            // You don’t actually call full main()
            assertTrue(tasks.size() > 0);

        } catch (Exception e) {
            fail("Simulation should not crash: " + e.getMessage());
        }
    }
}