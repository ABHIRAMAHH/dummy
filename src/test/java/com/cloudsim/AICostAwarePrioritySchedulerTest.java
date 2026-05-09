package com.cloudsim;

import com.cloudsim.scheduler.SchedulerUtil;
import com.cloudsim.workload.WorkloadLoader.WorkloadTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class AICostAwarePrioritySchedulerTest {

    // ✅ Test 1: CostVmSimple works correctly
    @Test
    void testCostVmSimple() {
        CostVmSimple vm = new CostVmSimple(1000, 2);
        vm.setCostPerSecond(50.0);

        assertEquals(50.0, vm.getCostPerSecond(), 0.001);
        assertEquals(1000, vm.getMips(), 0.001);
        assertEquals(2, vm.getNumberOfPes());
    }

    // ✅ Test 2: WorkloadPredictor fallback works
    @Test
    void testWorkloadPredictorFallback() {
        WorkloadPredictor predictor = new WorkloadPredictor("invalid.csv");
        double val = predictor.getPredictedThroughput(10.0);

        assertTrue(val > 0);
    }

    // ✅ Test 3: SchedulerUtil cost calculation
    @Test
    void testSchedulerUtilCost() {
        CostVmSimple vm = new CostVmSimple(1000, 2);
        vm.setCostPerSecond(10.0);

        double cost = SchedulerUtil.costPerSecond(vm);

        assertEquals(10.0, cost, 0.001);
    }

    // ✅ Test 4: Priority ranking logic
    @Test
    void testPriorityValue() {
        assertEquals(3, SchedulerUtil.priorityValue("High"));
        assertEquals(2, SchedulerUtil.priorityValue("Medium"));
        assertEquals(1, SchedulerUtil.priorityValue("Low"));
    }

    // ✅ Test 5: WorkloadTask creation sanity
    @Test
    void testWorkloadTaskCreation() {
        WorkloadTask task = new WorkloadTask();
        task.jobPriority = "High";
        task.taskExecutionTime = 1000;
        task.cpuUtilization = 50;
        task.startTime = LocalDateTime.now();

        assertNotNull(task);
        assertEquals("High", task.jobPriority);
    }
}