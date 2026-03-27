package com.cloudsim.scheduler;

import com.cloudsim.output.SimulationResult;
import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;

public final class SchedulerUtil {
    private SchedulerUtil() {}

    public static int priorityValue(String p) {
        if (p == null) return 1;
        switch (p.trim().toLowerCase()) {
            case "high":   return 3;
            case "medium": return 2;
            case "low":    return 1;
            default:
                try { return Integer.parseInt(p.trim()); }
                catch (Exception e) { return 1; }
        }
    }

    public static double lengthMiLike(WorkloadLoader.WorkloadTask t) {
        return Math.max(1000.0, t.taskExecutionTime * t.cpuUtilization);
    }

    public static double capacityMiPerSec(Vm vm) {
        return Math.max(1.0, vm.getMips() * vm.getNumberOfPes());
    }

    public static double costPerSecond(Vm vm) {
        try {
            if (vm instanceof com.cloudsim.CostVmSimple) {
                double c = ((com.cloudsim.CostVmSimple) vm).getCostPerSecond();
                return (c > 0.0) ? c : 0.01;
            }
        } catch (Throwable ignored) {}
        return 0.01;
    }

    /**
     * Shared pretty-print summary for all schedulers.
     * Mirrors the ASB-Dynamic v4 output format.
     *
     * @param schedulerName  e.g. "FIFO", "RoundRobin", "SJF", etc.
     * @param bindCounts     map of vmId → task count
     * @param vms            list of VMs
     * @param backlog        per-VM estimated backlog in seconds (pass null if not tracked)
     */
    /**
     * Builds a SchedulerResult object ready for JSON export.
     * Call this at the end of each scheduler, same place you call printSummary().
     */
    public static SimulationResult.SchedulerResult buildResult(
            String schedulerName,
            Map<Long, Integer> bindCounts,
            List<Vm> vms,
            double[] backlog,
            double throughputMin,       // pass -1 if not applicable
            double throughputMax,       // pass -1 if not applicable
            Map<String, Double> weights // pass null if not applicable
    ) {
        int V = vms.size();
        int total = bindCounts.values().stream().mapToInt(Integer::intValue).sum();
        double fairShare = (double) total / V;

        int[] taskCount = new int[V];
        for (int j = 0; j < V; j++)
            taskCount[j] = bindCounts.getOrDefault(vms.get(j).getId(), 0);

        double mean = Arrays.stream(taskCount).average().orElse(0);
        double var  = 0;
        for (int c : taskCount) var += Math.pow(c - mean, 2);
        double stdDev = Math.sqrt(var / V);

        SimulationResult.SchedulerResult sr = new SimulationResult.SchedulerResult();
        sr.name          = schedulerName;
        sr.fairShare     = fairShare;
        sr.loadStdDev    = Math.round(stdDev * 100.0) / 100.0;
        sr.throughputMin = throughputMin;
        sr.throughputMax = throughputMax;
        sr.weights       = weights;
        sr.vms           = new ArrayList<>();

        for (int j = 0; j < V; j++) {
            Vm vm = vms.get(j);
            sr.vms.add(new SimulationResult.VmResult(
                    (int) vm.getId(),
                    taskCount[j],
                    vm.getMips(),
                    costPerSecond(vm),
                    Math.round(backlog[j] * 100.0) / 100.0
            ));
        }

        return sr;
    }
    public static void printSummary(
            String schedulerName,
            Map<Long, Integer> bindCounts,
            List<Vm> vms,
            double[] backlog
    ) {
        int V = vms.size();
        int total = bindCounts.values().stream().mapToInt(Integer::intValue).sum();
        double fairShare = (double) total / V;

        int[] taskCount = new int[V];
        for (int j = 0; j < V; j++)
            taskCount[j] = bindCounts.getOrDefault(vms.get(j).getId(), 0);

        double mean = Arrays.stream(taskCount).average().orElse(0);
        double var  = 0;
        for (int c : taskCount) var += Math.pow(c - mean, 2);
        double stdDev = Math.sqrt(var / V);

        System.out.println("Final Bindings: " + bindCounts);
        System.out.println(
                "\n╔══════════════════════════════════════════════════════════╗");
        System.out.printf(
                "║  %-54s ║%n",
                schedulerName + " Scheduler — Assignment Summary");
        System.out.println(
                "╠══════════════════════════════════════════════════════════╣");
        System.out.printf(
                "║  Fair share per VM: %-5.0f                               ║%n",
                fairShare);
        System.out.println(
                "╠══════════════════════════════════════════════════════════╣");
        System.out.printf(
                "║  %-6s %-8s %-8s %-10s %-12s ║%n",
                "VM_ID", "Tasks", "MIPS", "Cost/Sec", "Backlog(s)");
        System.out.println(
                "╠══════════════════════════════════════════════════════════╣");

        for (int j = 0; j < V; j++) {
            Vm vm = vms.get(j);
            double bl = (backlog != null) ? backlog[j] : 0.0;
            String blStr = (backlog != null) ? String.format("%-10.2f", bl) : "N/A       ";
            String flag = taskCount[j] > 1.5 * fairShare ? " ⚠" : "  ";
            System.out.printf(
                    "║  %-6d %-8d %-8.0f %-10.2f %s%s ║%n",
                    vm.getId(), taskCount[j], vm.getMips(),
                    costPerSecond(vm), blStr, flag);
        }

        System.out.println(
                "╠══════════════════════════════════════════════════════════╣");
        System.out.printf(
                "║  Load StdDev: %-8.2f (target < %-6.0f)               ║%n",
                stdDev, fairShare * 0.3);
        System.out.println(
                "╚══════════════════════════════════════════════════════════╝\n");
    }
}