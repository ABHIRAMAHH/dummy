package com.cloudsim;

import com.cloudsim.output.ResultsExporter;
import com.cloudsim.output.SimulationResult;
import com.cloudsim.scheduler.*;
import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.ArrayList;
import java.util.List;

public class MainSimulation {

    // ── VM definitions (same across all schedulers) ──────────
    // Each scheduler gets FRESH copies of these specs
    private static final int[]    VM_MIPS     = {1000, 1000, 2000, 2000, 3000};
    private static final int[]    VM_PES      = {1,    1,    2,    2,    4   };
    private static final double[] VM_COST_SEC = {1.0,  1.0,  50.0, 50.0, 120.0};
    private static final int      VM_RAM      = 2048;
    private static final int      VM_BW       = 1000;
    private static final int      VM_SIZE     = 10000;

    // ── Host definitions ──────────────────────────────────────
    private static final int HOST_MIPS = 100000;
    private static final int HOST_PES  = 32;
    private static final int HOST_RAM  = 65536;
    private static final int HOST_BW   = 100000;
    private static final int HOST_SIZE = 1000000;

    // ── Scheduler names to run ────────────────────────────────
    private enum SchedulerType {
        AI_COST_PRIORITY,
        FIFO,
        ROUND_ROBIN,
        SJF,
        MIN_MIN,
        PRIORITY,
        PRIORITY_SJF,
        COST_ONLY_GREEDY
    }

    public static void main(String[] args) throws Exception {

        // Load workload ONCE — shared across all schedulers
        List<WorkloadLoader.WorkloadTask> tasks =
                WorkloadLoader.load("data/workload.csv");

        // Load predictor ONCE
        WorkloadPredictor predictor = new WorkloadPredictor("data/predictions.csv");


        System.out.println("✅ Loaded " + tasks.size() + " tasks.");
        System.out.println("Running all schedulers...\n");

        // ── Run each scheduler in its own CloudSim instance ──
        for (SchedulerType type : SchedulerType.values()) {
            System.out.println("\n" + "=".repeat(50));
            System.out.println("Running Scheduler: " + type);
            System.out.println("=".repeat(50));

            runScheduler(type, tasks, predictor);
        }

        // ── Export all results to one JSON file ───────────────
        ResultsExporter.export();
    }

    // ─────────────────────────────────────────────────────────
    // Runs ONE scheduler in a completely fresh CloudSim env
    // ─────────────────────────────────────────────────────────
    private static void runScheduler(
            SchedulerType type,
            List<WorkloadLoader.WorkloadTask> allTasks,
            WorkloadPredictor predictor
    ) {
        List<WorkloadLoader.WorkloadTask> tasks = new ArrayList<>(allTasks);

        CloudSim sim = new CloudSim();
        List<HostSimple> hosts = createHosts();
        DatacenterSimple datacenter = new DatacenterSimple(sim, hosts);
        datacenter.getCharacteristics()
                .setCostPerSecond(0).setCostPerMem(0)
                .setCostPerStorage(0).setCostPerBw(0);

        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(sim);
        List<Vm> vms = createVms(broker);

        // ── Step 1: scheduler runs → internally calls ResultsExporter.addResult()
        List<CloudletSimple> cloudlets;
        switch (type) {
            case AI_COST_PRIORITY:
                cloudlets = AICostAwarePriorityScheduler.schedule(tasks, vms, broker, predictor);
                break;
            case PRIORITY:
                cloudlets = PriorityScheduler.schedule(tasks, vms, broker, predictor);
                break;
            // ... other cases
            default:
                throw new IllegalStateException("Unknown: " + type);
        }

        // ── Step 2: simulation runs
        System.out.println("Starting simulation for: " + type);
        sim.start();

        // ── Step 3: collect post-sim metrics → calls enrichLastResult()
        collectAndStoreMetrics(type.name(), cloudlets, vms);

        System.out.println("✅ " + type + " done.\n");
    }

    // ─────────────────────────────────────────────────────────
    // Collect actual CloudSim metrics post-simulation
    // and merge with pre-simulation scheduler stats
    // ─────────────────────────────────────────────────────────
    private static void collectAndStoreMetrics(
            String schedulerName,
            List<CloudletSimple> cloudlets,
            List<Vm> vms
    ) {
        double totalExecTime = cloudlets.stream()
                .mapToDouble(c -> c.getActualCpuTime())
                .sum();

        double makespan = cloudlets.stream()
                .mapToDouble(c -> c.getFinishTime())
                .max().orElse(0);

        double avgWaitTime = cloudlets.stream()
                .mapToDouble(c -> c.getWaitingTime())
                .average().orElse(0);

        double totalCost = cloudlets.stream()
                .mapToDouble(c -> {
                    Vm vm = c.getVm();
                    return SchedulerUtil.costPerSecond(vm) * c.getActualCpuTime();
                }).sum();

        // Print so you can see them
        System.out.printf("📈 Post-Sim Metrics for %s:%n", schedulerName);
        System.out.printf("   Makespan     : %.2f s%n", makespan);
        System.out.printf("   Avg Wait Time: %.2f s%n", avgWaitTime);
        System.out.printf("   Total Cost   : %.2f%n",   totalCost);
        System.out.printf("   Total ExecTime: %.2f s%n", totalExecTime);

        ResultsExporter.enrichLastResult(makespan, avgWaitTime, totalCost, totalExecTime);
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────
    private static List<HostSimple> createHosts() {
        List<HostSimple> hosts = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int p = 0; p < HOST_PES; p++)
                peList.add(new PeSimple(HOST_MIPS));

            hosts.add((HostSimple) new HostSimple(HOST_RAM, HOST_BW, HOST_SIZE, peList)
                    .setVmScheduler(new VmSchedulerTimeShared()));
        }
        return hosts;
    }

    private static List<Vm> createVms(DatacenterBrokerSimple broker) {
        List<Vm> vms = new ArrayList<>();
        for (int j = 0; j < VM_MIPS.length; j++) {
            CostVmSimple vm = new CostVmSimple(VM_MIPS[j], VM_PES[j]);
            vm.setCostPerSecond(VM_COST_SEC[j]);
            vm.setRam(VM_RAM).setBw(VM_BW).setSize(VM_SIZE)
                    .setCloudletScheduler(new CloudletSchedulerTimeShared());
            broker.submitVm(vm);
            vms.add(vm);
        }
        return vms;
    }
}