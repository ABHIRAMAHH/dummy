package com.cloudsim;

import com.cloudsim.scheduler.FIFOScheduler;
import com.cloudsim.scheduler.RoundRobinScheduler;
import com.cloudsim.scheduler.SJFScheduler;
import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.vms.Vm;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerExecutionTest {

    private List<WorkloadLoader.WorkloadTask> createDummyTasks() {
        List<WorkloadLoader.WorkloadTask> tasks = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            WorkloadLoader.WorkloadTask t = new WorkloadLoader.WorkloadTask();
            t.taskId = i;
            t.cpuUtilization = 50;
            t.taskExecutionTime = 1000;
            t.arrivalSeconds = i;
            t.jobPriority = "Medium";
            tasks.add(t);
        }
        return tasks;
    }

    private List<Vm> createDummyVMs(CloudSim sim) {
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            CostVmSimple vm = new CostVmSimple(1000, 1);
            vm.setCostPerSecond(10);
            vms.add(vm);
        }
        return vms;
    }

    @Test
    void testFIFOSchedulerRuns() {
        CloudSim sim = new CloudSim();
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(sim);

        List<WorkloadLoader.WorkloadTask> tasks = createDummyTasks();
        List<Vm> vms = createDummyVMs(sim);

        assertDoesNotThrow(() -> {
            FIFOScheduler.schedule(tasks, vms, broker);
        });
    }

    @Test
    void testRoundRobinSchedulerRuns() {
        CloudSim sim = new CloudSim();
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(sim);

        List<WorkloadLoader.WorkloadTask> tasks = createDummyTasks();
        List<Vm> vms = createDummyVMs(sim);

        assertDoesNotThrow(() -> {
            RoundRobinScheduler.schedule(tasks, vms, broker);
        });
    }

    @Test
    void testSJFSchedulerRuns() {
        CloudSim sim = new CloudSim();
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(sim);

        List<WorkloadLoader.WorkloadTask> tasks = createDummyTasks();
        List<Vm> vms = createDummyVMs(sim);

        assertDoesNotThrow(() -> {
            SJFScheduler.schedule(tasks, vms, broker);
        });
    }
    @Test
    void testPrioritySchedulerRuns() {
        CloudSim sim = new CloudSim();
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(sim);

        List<WorkloadLoader.WorkloadTask> tasks = createDummyTasks();
        List<Vm> vms = createDummyVMs(sim);

        WorkloadPredictor predictor = new WorkloadPredictor("invalid.csv");

        assertDoesNotThrow(() -> {
            com.cloudsim.scheduler.PriorityScheduler.schedule(tasks, vms, broker, predictor);
        });
    }
}