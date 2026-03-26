package com.cloudsim.scheduler;

import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;


import com.cloudsim.WorkloadPredictor;

public class PriorityScheduler {

    private static int priorityValue(String p) {
        if (p == null) return 1;
        switch (p.trim().toLowerCase()) {
            case "high": return 3;
            case "medium": return 2;
            case "low": return 1;
            default:
                try { return Integer.parseInt(p.trim()); }
                catch (Exception e) { return 1; }
        }
    }

    /** Must match WorkloadLoader.createCloudletFrom(): length = exec_ms * cpu_util */
    private static double estimatedExecTimeSeconds(WorkloadLoader.WorkloadTask t, Vm vm) {
        double length = Math.max(100.0, (t.taskExecutionTime * t.cpuUtilization) / 50); // "MI-like"
        double vmMips = Math.max(1.0, vm.getMips());
        return length / vmMips;
    }

    /** VM cost per second from your custom CostVmSimple; safe fallback otherwise. */
    private static double vmCostPerSecond(Vm vm) {
        try {
            if (vm instanceof com.cloudsim.CostVmSimple) {
                double c = ((com.cloudsim.CostVmSimple) vm).getCostPerSecond();
                return (c > 0.0) ? c : 0.01;
            }
        } catch (Throwable ignored) {}
        return 0.01;
    }

    /**
     * Scheduler:
     * - Sort: (-priority, arrival)
     * - Choose VM by minimizing: completionTime + LAMBDA_COST * dynamicCostScore
     * - Tie-break: ready time, then completion time
     * - Offset rotation reduces early-VM bias when objectives are close
     *
     * Changes made:
     *  - Added ROTATE_EVERY_TASKS (rotate offset every N tasks, not every task)
     *  - Kept LAMBDA_COST in balanced range (you can tune 0.05..0.30)
     */

    public static List<CloudletSimple> schedule(
            List<WorkloadLoader.WorkloadTask> tasks,
            List<Vm> vms,
            DatacenterBrokerSimple broker,
            WorkloadPredictor predictor
    ) {
        // Sort by priority desc, then arrival asc
        tasks.sort((a, b) -> {
            int pa = priorityValue(a.jobPriority);
            int pb = priorityValue(b.jobPriority);
            if (pa != pb) return Integer.compare(pb, pa);
            return Double.compare(a.getArrivalTimeSeconds(), b.getArrivalTimeSeconds());
        });

        // Backlog model per VM (in "MI-like units" consistent with your length definition)
        Map<Vm, Double> backlogMi = new HashMap<>();
        Map<Vm, Double> lastUpd  = new HashMap<>();
        for (Vm vm : vms) {
            backlogMi.put(vm, 0.0);
            lastUpd.put(vm, 0.0);
        }

        // Tune this: higher => more cost-sensitive (can make distribution more skewed)
        final double LAMBDA_COST = 0.01;

        // epsilon for float comparisons
        final double EPS = 1e-6;
        final int ROTATE_EVERY_TASKS = 20;

        List<CloudletSimple> cloudlets = new ArrayList<>();
        int offset = 0;
        int scheduled=0;
        Map<Long, Integer> bindCounts = new TreeMap<>();

        for (WorkloadLoader.WorkloadTask t : tasks) {
            CloudletSimple c = WorkloadLoader.createCloudletFrom(t);

            double arrival = t.getArrivalTimeSeconds();
            try { c.setSubmissionDelay(arrival); } catch (NoSuchMethodError ignore) {}

            // predicted throughput (optional modifier)
            double predTh = 5.0;
            try {
                if (predictor != null) predTh = predictor.getPredictedThroughput(arrival);
            } catch (Throwable ignored) {}

            // Your workload length model (must match createCloudletFrom)
            double lengthMi = Math.max(1000.0, t.taskExecutionTime * t.cpuUtilization);

            Vm bestVm = null;
            double bestObjective = Double.MAX_VALUE;
            double bestExpectedCost = Double.MAX_VALUE;
            double bestCompletion = Double.MAX_VALUE;

            for (int k = 0; k < vms.size(); k++) {
                Vm vm = vms.get((k + offset) % vms.size());

                // VM capacity in MI/sec (approx): mips per PE × number of PEs
                double capacity = Math.max(1.0, vm.getMips() * vm.getNumberOfPes());

                // Decay backlog up to this arrival time
                double prevT = lastUpd.getOrDefault(vm, 0.0);
                double backlog = backlogMi.getOrDefault(vm, 0.0);

                double dt = Math.max(0.0, arrival - prevT);
                backlog = Math.max(0.0, backlog - capacity * dt);

                // If we assign this cloudlet to this VM:
                double newBacklog = backlog + lengthMi;
                if (newBacklog / capacity > 200) continue; // skip overloaded VM
                double completion = arrival + (newBacklog / capacity);
                double loadFactor = newBacklog / capacity;

// exponential penalty (VERY IMPORTANT)
                double loadPenalty = Math.pow(loadFactor, 1.5);

                completion += loadPenalty;
                double baseCostPerSec = vmCostPerSecond(vm);

                // optional throughput adjustment (keep if you want predictor influence)
                double adjustedCostPerSec = baseCostPerSec / (1.0 + predTh / 10.0);

                // convert $/sec to $/MI-like using capacity
                double costPerMi = adjustedCostPerSec / capacity;

                double cpuUtil = 0.5;
                try {
                    if (vm.getHost() != null) cpuUtil = Math.max(0.01, vm.getHost().getCpuMipsUtilization());
                } catch (Throwable ignored) {}

                double dynamicCostMultiplier = (1.0 + 0.3 * cpuUtil);

                double expectedCost = lengthMi * costPerMi * dynamicCostMultiplier;
                double normCompletion = completion;
                double normCost = expectedCost / 100.0; // normalize cost scale

                double objective = normCompletion + LAMBDA_COST * normCost;
                objective += Math.random() * 0.01;
                int assigned = bindCounts.getOrDefault(vm.getId(), 0);
                if (assigned > tasks.size() / vms.size() + 50) continue;
                objective += assigned * 0.2;
                // Choose minimal objective; tie-break by lower expectedCost, then earlier completion
                if (objective < bestObjective - EPS
                        || (Math.abs(objective - bestObjective) <= EPS && expectedCost < bestExpectedCost - EPS)
                        || (Math.abs(objective - bestObjective) <= EPS && Math.abs(expectedCost - bestExpectedCost) <= EPS
                        && completion < bestCompletion - EPS)) {
                    bestObjective = objective;
                    bestExpectedCost = expectedCost;
                    bestCompletion = completion;
                    bestVm = vm;
                }
            }

            if (bestVm == null) bestVm = vms.get(0);

            broker.bindCloudletToVm(c, bestVm);
            c.setVm(bestVm);

            bindCounts.merge(bestVm.getId(), 1, Integer::sum);

//            if (cloudlets.size() == 50 || cloudlets.size() == 200 || cloudlets.size() == 1000) {
////                System.out.println("Bindings so far: " + bindCounts);
//            }
            if (cloudlets.size() == 1000) {
                System.out.println("Final Bindings: " + bindCounts);
            }
            cloudlets.add(c);
            scheduled++;

            // IMPORTANT: update backlog consistently for the chosen VM
            double capacity = Math.max(1.0, bestVm.getMips() * bestVm.getNumberOfPes());

            double prevT = lastUpd.getOrDefault(bestVm, 0.0);
            double backlog = backlogMi.getOrDefault(bestVm, 0.0);

            double dt = Math.max(0.0, arrival - prevT);
            backlog = Math.max(0.0, backlog - capacity * dt);

            backlogMi.put(bestVm, backlog + lengthMi);
            lastUpd.put(bestVm, arrival);
            if (cloudlets.size() == 50 || cloudlets.size() == 200 || cloudlets.size() == 1000) {
                System.out.println("Bindings so far: " + bindCounts);
            }

            if ((scheduled % ROTATE_EVERY_TASKS) == 0) {
                offset = (offset + 1) % vms.size();
            }
        }
        System.out.println("Final Bindings"+bindCounts);
        broker.submitCloudletList(cloudlets);
        System.out.println("USING PriorityScheduler v2 (backlog/capacity model) " + System.currentTimeMillis());
        return cloudlets;

    }
}