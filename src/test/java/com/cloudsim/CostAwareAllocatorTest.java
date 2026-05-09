package com.cloudsim;

import com.cloudsim.allocater.CostAwareAllocater;
import org.cloudbus.cloudsim.vms.Vm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class CostAwareAllocatorTest {
    @Test
    void testAllocatorBasic() {
        WorkloadPredictor predictor = new WorkloadPredictor("invalid.csv");
        CostAwareAllocater alloc = new CostAwareAllocater(predictor);

        Vm vm = new CostVmSimple(1000, 1);
        vm.setId(1);

        boolean result = alloc.allocateHostForVm(vm);

        assertFalse(result); // no host → expected
    }
}
