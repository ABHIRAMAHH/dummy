package com.cloudsim;

import com.cloudsim.allocater.CostAwareAllocater;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.vms.Vm;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AllocaterTest {

    @Test
    void testAllocaterRuns() {

        CloudSim sim = new CloudSim();

        List<Pe> peList = new ArrayList<>();
        peList.add(new PeSimple(1000));

        Host host = new HostSimple(2048, 10000, 100000, peList);

        List<Host> hosts = new ArrayList<>();
        hosts.add(host);

        WorkloadPredictor predictor = new WorkloadPredictor("invalid.csv");

        CostAwareAllocater allocater = new CostAwareAllocater(predictor);

        Vm vm = new CostVmSimple(1000, 1);

        assertDoesNotThrow(() -> {
            allocater.allocateHostForVm(vm);
        });
    }
    @Test
    void testAllocatorNoHost() {
        WorkloadPredictor predictor = new WorkloadPredictor("invalid.csv");
        CostAwareAllocater alloc = new CostAwareAllocater(predictor);

        Vm vm = new CostVmSimple(1000, 1);
        vm.setId(1);

        boolean result = alloc.allocateHostForVm(vm);

        assertFalse(result);
    }
}