package com.cloudsim;

import com.cloudsim.scheduler.SchedulerUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BasicTests {

    // ✅ Test 1: WorkloadPredictor
    @Test
    void testPredictorFallback() {
        WorkloadPredictor predictor = new WorkloadPredictor("invalid.csv");
        double val = predictor.getPredictedThroughput(10.0);
        assertTrue(val > 0);
    }

    // ✅ Test 2: CostVmSimple
    @Test
    void testCostVm() {
        CostVmSimple vm = new CostVmSimple(1000, 2);
        vm.setCostPerSecond(50);
        assertEquals(50, vm.getCostPerSecond());
    }

    // ✅ Test 3: SchedulerUtil
    @Test
    void testCostPerSecondDefault() {
        CostVmSimple vm = new CostVmSimple(1000, 2);
        double cost = SchedulerUtil.costPerSecond(vm);
        assertTrue(cost >= 0);
    }
}