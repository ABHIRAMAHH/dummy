//package com.cloudsim;
//
//import com.cloudsim.allocater.CostAwareAllocater;
//import com.cloudsim.config.ConfigLoader;
//import com.cloudsim.model.SimulationResult;
//import com.cloudsim.scheduler.*;
//import com.cloudsim.workload.WorkloadLoader;
//import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
//import org.cloudbus.cloudsim.cloudlets.Cloudlet;
//import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
//import org.cloudbus.cloudsim.core.CloudSim;
//import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
//import org.cloudbus.cloudsim.hosts.Host;
//import org.cloudbus.cloudsim.hosts.HostSimple;
//import org.cloudbus.cloudsim.resources.Pe;
//import org.cloudbus.cloudsim.resources.PeSimple;
//import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
//import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
//import org.cloudbus.cloudsim.vms.Vm;
//
//import java.io.FileInputStream;
//import java.io.InputStream;
//import java.util.*;
//
//public class Main {
//
//    // Simple container to hold all 3 metrics per scheduler
//    static class Metrics {
//        double makespan;
//        double avgWaitTime;
//        double totalCost;
//
//        Metrics(double makespan, double avgWaitTime, double totalCost) {
//            this.makespan    = makespan;
//            this.avgWaitTime = avgWaitTime;
//            this.totalCost   = totalCost;
//        }
//    }
//
//    public static void main(String[] args) throws Exception {
//        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off");
//
//        final String workloadCsv = "src/main/java/com/cloudsim/workload/cloud_workload_dataset.csv";
//        final String lstmCsv     = "C:\\Users\\HP\\OneDrive\\Scans\\Desktop\\New folder\\cloud-cost-scheduler\\results\\cloud_workload_with_predictions_lstm.csv";
//        final String vmTypesPath = "src/main/java/com/cloudsim/config/vm_types.json";
//
//        InputStream in = new FileInputStream(workloadCsv);
//        List<WorkloadLoader.WorkloadTask> tasks = WorkloadLoader.load(in);
//
//        WorkloadPredictor predictor = new WorkloadPredictor(lstmCsv);
//
//        List<VmTemplate> templates = ConfigLoader.loadVmTemplates(vmTypesPath);
//        if (templates == null || templates.isEmpty()) {
//            throw new IllegalStateException("No VM templates loaded");
//        }
//
//        Map<SchedulerType, Metrics> results = new LinkedHashMap<>();
//
//        for (SchedulerType type : SchedulerType.values()) {
//            Metrics m = runSimulation(type, new ArrayList<>(tasks), templates, predictor);
//            results.put(type, m);
//        }
//
//        // ── Rich comparison table ──────────────────────────────────
//        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════════╗");
//        System.out.println("║                        SCHEDULER COMPARISON (for paper)                         ║");
//        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
//        System.out.printf("║ %-20s %-12s %-14s %-12s %-12s %-8s ║%n",
//                "Scheduler", "Makespan(s)", "AvgWaitTime(s)", "TotalCost", "vs FIFO", "Improv%");
//        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
//
//        double baseline = results.get(SchedulerType.FIFO).makespan;
//        double aiBest   = results.get(SchedulerType.AI_COST_PRIORITY).makespan;
//
//        for (var e : results.entrySet()) {
//            Metrics m    = e.getValue();
//            double saved = baseline - m.makespan;
//            double pct   = (saved / baseline) * 100.0;
//            String marker = e.getKey() == SchedulerType.AI_COST_PRIORITY ? " ◄ AI" : "";
//            System.out.printf("║ %-20s %-12.2f %-14.2f %-12.2f %-12.2f %-6.1f%% %-4s ║%n",
//                    e.getKey(), m.makespan, m.avgWaitTime, m.totalCost, saved, pct, marker);
//        }
//
//        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════╝");
//
//        if (aiBest < baseline) {
//            System.out.printf("%n✅ ASB-Dynamic improves makespan by %.1f%% over FIFO baseline%n",
//                    ((baseline - aiBest) / baseline) * 100.0);
//        } else {
//            System.out.println("\n⚠ AI scheduler did not beat FIFO — check workload/VM config");
//        }
//    }
//
//    private static DatacenterSimple createDatacenter(CloudSim sim, WorkloadPredictor predictor) {
//        List<Host> hostList = new ArrayList<>();
//
//        for (int h = 0; h < 3; h++) {
//            List<Pe> peList = new ArrayList<>();
//            for (int i = 0; i < 10; i++) peList.add(new PeSimple(10_000));
//
//            int  ram     = 131_072 + h * 65_536;
//            long bw      = 10_000_000;
//            long storage = 1_000_000_000;
//
//            HostSimple host = new HostSimple(ram, bw, storage, peList);
//            host.setVmScheduler(new VmSchedulerTimeShared());
//            hostList.add(host);
//        }
//
//        CostAwareAllocater policy = new CostAwareAllocater(predictor);
//        return new DatacenterSimple(sim, hostList, policy);
//    }
//
//    // ── Now returns Metrics (makespan + avgWaitTime + totalCost) ──
//    private static Metrics runSimulation(
//            SchedulerType type,
//            List<WorkloadLoader.WorkloadTask> tasks,
//            List<VmTemplate> templates,
//            WorkloadPredictor predictor
//    ) throws Exception {
//
//        System.out.println("\n==============================");
//        System.out.println("Running Scheduler: " + type);
//        System.out.println("==============================");
//
//        CloudSim sim = new CloudSim();
//        DatacenterSimple dc = createDatacenter(sim, predictor);
//        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(sim);
//
//        int[] pick = {0, 0, 1, 1, 2};
//        List<Vm> vms = new ArrayList<>();
//        int vmId = 0;
//
//        for (int i = 0; i < pick.length; i++) {
//            VmTemplate t = templates.get(pick[i]);
//
//            CostVmSimple vm = new CostVmSimple(t.mips, t.pes);
//            vm.setId(vmId++);
//            vm.setRam(t.ram);
//            vm.setBw(1_000_000);
//            vm.setSize(10_000);
//            vm.setCloudletScheduler(new CloudletSchedulerTimeShared());
//            vm.setCostPerSecond(t.costPerSec);
//
//            vms.add(vm);
//        }
//
//        broker.submitVmList(vms);
//
//        List<CloudletSimple> cloudlets;
//
//        switch (type) {
//            case PRIORITY:
//                cloudlets = PriorityScheduler.schedule(tasks, vms, broker, predictor);
//                break;
//            case AI_COST_PRIORITY:
//                cloudlets = AICostAwarePriorityScheduler.schedule(tasks, vms, broker, predictor);
//                break;
//            case ROUND_ROBIN:
//                cloudlets = RoundRobinScheduler.schedule(tasks, vms, broker);
//                break;
//            case FIFO:
//                cloudlets = FIFOScheduler.schedule(tasks, vms, broker);
//                break;
//            case SJF:
//                cloudlets = SJFScheduler.schedule(tasks, vms, broker);
//                break;
//            case MINMIN:
//                cloudlets = MinMinScheduler.schedule(tasks, vms, broker);
//                break;
//            case COST_GREEDY:
//                cloudlets = CostOnlyGreedyScheduler.schedule(tasks, vms, broker);
//                break;
//            case PRIORITY_SJF:
//                cloudlets = PrioritySJFScheduler.schedule(tasks, vms, broker, predictor);
//                break;
//            default:
//                throw new IllegalStateException("Unknown scheduler");
//        }
//
//        System.out.println("Starting simulation for: " + type);
//        sim.start();
//        System.out.println("Completed simulation for: " + type);
//
//        List<Cloudlet> finished = broker.getCloudletFinishedList();
//
//        // ── Makespan ──
//        double makespan = finished.stream()
//                .mapToDouble(Cloudlet::getFinishTime)
//                .max().orElse(0);
//
//        // ── AvgWaitTime = avg(execStartTime - submissionDelay) ──
//        double avgWaitTime = finished.stream()
//                .mapToDouble(c -> Math.max(0, c.getExecStartTime() - c.getSubmissionDelay()))
//                .average().orElse(0);
//
//        // ── TotalCost = sum(actualCpuTime * costPerSecond) ──
//        double totalCost = finished.stream()
//                .mapToDouble(c -> {
//                    double execTime   = c.getActualCpuTime();
//                    Vm     vm         = c.getVm();
//                    double costPerSec = 0.01;
//                    if (vm instanceof CostVmSimple)
//                        costPerSec = ((CostVmSimple) vm).getCostPerSecond();
//                    return execTime * costPerSec;
//                }).sum();
//
//        // ── Print result block (paste this to me for JSON) ──
//        System.out.println("\n=== RESULT ===");
//        System.out.println("Scheduler: "   + type);
//        System.out.printf("Makespan: %.2f%n",     makespan);
//        System.out.printf("AvgWaitTime: %.2f%n",  avgWaitTime);
//        System.out.printf("TotalCost: %.2f%n",    totalCost);
//        System.out.println("=== END RESULT ===");
//
//        return new Metrics(makespan, avgWaitTime, totalCost);
//    }
//
//    // ── API method (kept for Spring/REST use) ──
//    public static SimulationResult runSimulationAPI(SchedulerType type) throws Exception {
//
//        final String workloadCsv = "src/main/java/com/cloudsim/workload/cloud_workload_dataset.csv";
//        final String lstmCsv     = "C:\\Users\\HP\\...\\cloud_workload_with_predictions_lstm.csv";
//        final String vmTypesPath = "src/main/java/com/cloudsim/config/vm_types.json";
//
//        InputStream in = new FileInputStream(workloadCsv);
//        List<WorkloadLoader.WorkloadTask> tasks = WorkloadLoader.load(in);
//
//        WorkloadPredictor predictor = new WorkloadPredictor(lstmCsv);
//        List<VmTemplate>  templates = ConfigLoader.loadVmTemplates(vmTypesPath);
//
//        Metrics m = runSimulation(type, tasks, templates, predictor);
//
//        Map<Long, Integer> vmBindings = new HashMap<>();
//        return new SimulationResult(vmBindings, m.totalCost, m.makespan, 0.5);
//    }
//}
package com.cloudsim;

import com.cloudsim.allocater.CostAwareAllocater;
import com.cloudsim.config.ConfigLoader;
import com.cloudsim.model.SimulationResult;
import com.cloudsim.scheduler.*;
import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.vms.Vm;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.OutputStream;
import java.util.*;

public class Main {

    static class Metrics {
        double makespan;
        double avgWaitTime;
        double totalCost;

        Metrics(double makespan, double avgWaitTime, double totalCost) {
            this.makespan    = makespan;
            this.avgWaitTime = avgWaitTime;
            this.totalCost   = totalCost;
        }
    }

    public static void main(String[] args) throws Exception {

        // ── Suppress logging ──────────────────────────────────────
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off");
        java.util.logging.LogManager.getLogManager().reset();

        // ── Filter out CloudSim noise, keep your own prints ───────
        final PrintStream originalOut = System.out;
        System.setOut(new PrintStream(System.out) {
            private boolean shouldBlock(String x) {
                if (x == null) return true;
                return x.contains("Cloudlet")
                        || x.contains("cloudlet")
                        || x.contains("DatacenterBroker")
                        || x.contains("VmAllocationPolicy")
                        || x.contains("Datacenter")
                        || x.contains("CloudSim")
                        || x.contains("Clock")
                        || x.contains("INFO")
                        || x.contains("WARN")
                        || x.contains("DEBUG")
                        || x.contains("broker")
                        || x.contains("vm")
                        || x.contains("host")
                        || x.contains("simulation");
            }

            @Override
            public void println(String x) {
                if (!shouldBlock(x)) originalOut.println(x);
            }

            @Override
            public void print(String x) {
                if (!shouldBlock(x)) originalOut.print(x);
            }

            @Override
            public PrintStream printf(String format, Object... args) {
                originalOut.printf(format, args);
                return this;
            }

            @Override
            public void println(Object x) {
                println(x == null ? "null" : x.toString());
            }

            @Override
            public void print(Object x) {
                print(x == null ? "null" : x.toString());
            }
        });

        final String workloadCsv = "src/main/java/com/cloudsim/workload/cloud_workload_dataset.csv";
        final String lstmCsv     = "C:\\Users\\HP\\OneDrive\\Scans\\Desktop\\New folder\\cloud-cost-scheduler\\results\\cloud_workload_with_predictions_lstm.csv";
        final String vmTypesPath = "src/main/java/com/cloudsim/config/vm_types.json";

        InputStream in = new FileInputStream(workloadCsv);
        List<WorkloadLoader.WorkloadTask> tasks = WorkloadLoader.load(in);

        WorkloadPredictor predictor = new WorkloadPredictor(lstmCsv);

        List<VmTemplate> templates = ConfigLoader.loadVmTemplates(vmTypesPath);
        if (templates == null || templates.isEmpty()) {
            throw new IllegalStateException("No VM templates loaded");
        }

        Map<SchedulerType, Metrics> results = new LinkedHashMap<>();

        for (SchedulerType type : SchedulerType.values()) {
            Metrics m = runSimulation(type, new ArrayList<>(tasks), templates, predictor, originalOut);
            results.put(type, m);
        }

        // ── Rich comparison table ─────────────────────────────────
        originalOut.println("\n╔══════════════════════════════════════════════════════════════════════════════════╗");
        originalOut.println("║                        SCHEDULER COMPARISON (for paper)                         ║");
        originalOut.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        originalOut.printf("║ %-20s %-12s %-14s %-12s %-12s %-8s ║%n",
                "Scheduler", "Makespan(s)", "AvgWaitTime(s)", "TotalCost", "vs FIFO", "Improv%");
        originalOut.println("╠══════════════════════════════════════════════════════════════════════════════════╣");

        double baseline = results.get(SchedulerType.FIFO).makespan;
        double aiBest   = results.get(SchedulerType.AI_COST_PRIORITY).makespan;

        for (var e : results.entrySet()) {
            Metrics m    = e.getValue();
            double saved = baseline - m.makespan;
            double pct   = (saved / baseline) * 100.0;
            String marker = e.getKey() == SchedulerType.AI_COST_PRIORITY ? " ◄ AI" : "";
            originalOut.printf("║ %-20s %-12.2f %-14.2f %-12.2f %-12.2f %-6.1f%% %-4s ║%n",
                    e.getKey(), m.makespan, m.avgWaitTime, m.totalCost, saved, pct, marker);
        }

        originalOut.println("╚══════════════════════════════════════════════════════════════════════════════════╝");

        if (aiBest < baseline) {
            originalOut.printf("%n✅ ASB-Dynamic improves makespan by %.1f%% over FIFO baseline%n",
                    ((baseline - aiBest) / baseline) * 100.0);
        } else {
            originalOut.println("\n⚠ AI scheduler did not beat FIFO — check workload/VM config");
        }

        originalOut.flush();
    }

    private static DatacenterSimple createDatacenter(CloudSim sim, WorkloadPredictor predictor) {
        List<Host> hostList = new ArrayList<>();

        for (int h = 0; h < 3; h++) {
            List<Pe> peList = new ArrayList<>();
            for (int i = 0; i < 10; i++) peList.add(new PeSimple(10_000));

            int  ram     = 131_072 + h * 65_536;
            long bw      = 10_000_000;
            long storage = 1_000_000_000;

            HostSimple host = new HostSimple(ram, bw, storage, peList);
            host.setVmScheduler(new VmSchedulerTimeShared());
            hostList.add(host);
        }

        CostAwareAllocater policy = new CostAwareAllocater(predictor);
        return new DatacenterSimple(sim, hostList, policy);
    }

    private static Metrics runSimulation(
            SchedulerType type,
            List<WorkloadLoader.WorkloadTask> tasks,
            List<VmTemplate> templates,
            WorkloadPredictor predictor,
            PrintStream out
    ) throws Exception {

        CloudSim sim = new CloudSim();
        DatacenterSimple dc = createDatacenter(sim, predictor);
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(sim);

        int[] pick = {0, 0, 1, 1, 2};
        List<Vm> vms = new ArrayList<>();
        int vmId = 0;

        for (int i = 0; i < pick.length; i++) {
            VmTemplate t = templates.get(pick[i]);

            CostVmSimple vm = new CostVmSimple(t.mips, t.pes);
            vm.setId(vmId++);
            vm.setRam(t.ram);
            vm.setBw(1_000_000);
            vm.setSize(10_000);
            vm.setCloudletScheduler(new CloudletSchedulerTimeShared());
            vm.setCostPerSecond(t.costPerSec);

            vms.add(vm);
        }

        broker.submitVmList(vms);

        List<CloudletSimple> cloudlets;

        switch (type) {
            case PRIORITY:
                cloudlets = PriorityScheduler.schedule(tasks, vms, broker, predictor);
                break;
            case AI_COST_PRIORITY:
                cloudlets = AICostAwarePriorityScheduler.schedule(tasks, vms, broker, predictor);
                break;
            case ROUND_ROBIN:
                cloudlets = RoundRobinScheduler.schedule(tasks, vms, broker);
                break;
            case FIFO:
                cloudlets = FIFOScheduler.schedule(tasks, vms, broker);
                break;
            case SJF:
                cloudlets = SJFScheduler.schedule(tasks, vms, broker);
                break;
            case MINMIN:
                cloudlets = MinMinScheduler.schedule(tasks, vms, broker);
                break;
            case COST_GREEDY:
                cloudlets = CostOnlyGreedyScheduler.schedule(tasks, vms, broker);
                break;
            case PRIORITY_SJF:
                cloudlets = PrioritySJFScheduler.schedule(tasks, vms, broker, predictor);
                break;
            default:
                throw new IllegalStateException("Unknown scheduler");
        }

        sim.start();

        List<Cloudlet> finished = broker.getCloudletFinishedList();

        // ── Makespan ──
        double makespan = finished.stream()
                .mapToDouble(Cloudlet::getFinishTime)
                .max().orElse(0);

        // ── AvgWaitTime ──
        double avgWaitTime = finished.stream()
                .mapToDouble(c -> Math.max(0, c.getExecStartTime() - c.getSubmissionDelay()))
                .average().orElse(0);

        // ── TotalCost ──
        double totalCost = finished.stream()
                .mapToDouble(c -> {
                    double execTime   = c.getActualCpuTime();
                    Vm     vm         = c.getVm();
                    double costPerSec = 0.01;
                    if (vm instanceof CostVmSimple)
                        costPerSec = ((CostVmSimple) vm).getCostPerSecond();
                    return execTime * costPerSec;
                }).sum();

        // ── Print result directly to originalOut (always visible) ──
        out.println("\n=== RESULT ===");
        out.println("Scheduler: "  + type);
        out.printf("Makespan: %.2f%n",    makespan);
        out.printf("AvgWaitTime: %.2f%n", avgWaitTime);
        out.printf("TotalCost: %.2f%n",   totalCost);
        out.println("=== END RESULT ===");
        out.flush();

        return new Metrics(makespan, avgWaitTime, totalCost);
    }

    // ── API method (kept for Spring/REST use) ──
    public static SimulationResult runSimulationAPI(SchedulerType type) throws Exception {

        final String workloadCsv = "src/main/java/com/cloudsim/workload/cloud_workload_dataset.csv";
        final String lstmCsv     = "C:\\Users\\HP\\...\\cloud_workload_with_predictions_lstm.csv";
        final String vmTypesPath = "src/main/java/com/cloudsim/config/vm_types.json";

        InputStream in = new FileInputStream(workloadCsv);
        List<WorkloadLoader.WorkloadTask> tasks = WorkloadLoader.load(in);

        WorkloadPredictor predictor = new WorkloadPredictor(lstmCsv);
        List<VmTemplate>  templates = ConfigLoader.loadVmTemplates(vmTypesPath);

        Metrics m = runSimulation(type, tasks, templates, predictor, System.out);

        Map<Long, Integer> vmBindings = new HashMap<>();
        return new SimulationResult(vmBindings, m.totalCost, m.makespan, 0.5);
    }
}