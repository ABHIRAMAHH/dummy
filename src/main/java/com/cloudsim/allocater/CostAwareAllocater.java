//
package com.cloudsim.allocater;

import com.cloudsim.WorkloadPredictor;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 🤖 CostAwareAllocater (improved)
 *
 * - Uses WorkloadPredictor to obtain a predicted throughput (with small jitter).
 * - Computes a dynamic host cost and a score = utilization * hostCost.
 * - Stores vm -> score in vmCostMap.
 * - Attempts to notify predictor with an observed-throughput sample (if method exists).
 * - Prints an allocation summary once all VMs are allocated.
 */
public class CostAwareAllocater extends VmAllocationPolicySimple {

    private final Map<Vm, Double> vmCostMap = new LinkedHashMap<>();
    private final WorkloadPredictor predictor;

    public CostAwareAllocater(WorkloadPredictor predictor) {
        super();
        this.predictor = predictor;
    }

    @Override
    public boolean allocateHostForVm(Vm vm) {
        List<Host> hostList = getHostList();
        Host bestHost = null;
        double bestScore = Double.MAX_VALUE;

        // get a time-based predicted throughput and add a small jitter per-VM
        LocalDateTime now = LocalDateTime.now();
        double predictedThroughput = 1.0; // safe default
        try {
            if (predictor != null) {
                predictedThroughput = predictor.getPredictedThroughput(now);
            }
        } catch (Exception e) {
            // fallback: keep default
        }
        predictedThroughput = predictedThroughput * (0.95 + Math.random() * 0.1); // ±5% jitter

        for (Host host : hostList) {
            if (!host.isSuitableForVm(vm)) continue;

            double utilization = Math.max(0.01, host.getCpuMipsUtilization());
            double hostCost = calculateHostCost(host, predictedThroughput);
            double score = utilization * hostCost;

            if (score < bestScore) {
                bestScore = score;
                bestHost = host;
            }
        }

        if (bestHost != null) {
            // allocate via parent helper (keeps CloudSim semantics)
            boolean success;
            try {
                success = super.allocateHostForVm(vm, bestHost);
            } catch (NoSuchMethodError e) {
                // some CloudSim versions have different signatures — try bestHost.createVm as fallback
                success = bestHost.createVm(vm);
            }

            if (success) {
                // store score
                vmCostMap.put(vm, bestScore);

                // try to give feedback to predictor (if method exists) — call defensively
                try {
                    if (predictor != null) {
                        // create a noisy observed throughput sample for feedback
                        double observedSample = predictedThroughput * (0.5 + Math.random() * 1.0);
                        predictor.updateObservedThroughput(observedSample);
                    }
                } catch (NoSuchMethodError | AbstractMethodError | Exception ignored) {
                    // predictor may not implement updateObservedThroughput — ignore safely
                }

                System.out.printf(
                        "🤖 VM %d allocated → Host %d | Score: %.3f | PredThroughput: %.3f%n",
                        vm.getId(),
                        bestHost.getId(),
                        bestScore,
                        predictedThroughput
                );

                // if we've allocated as many VMs as we expect (vmCostMap contains all allocated VMs),
                // print a summary. We print when vmCostMap size matches total VMs created so far by broker.
                try {
                    // number of VMs known by datacenter broker may be unavailable here; use vm count placed
                    if (vmCostMap.size() >= getTotalAllocatedVmCountEstimate()) {
                        printAllocationSummary();
                    }
                } catch (Exception ignored) {
                }
            }
            return success;
        }

        System.out.printf("⚠ VM %d could not be allocated — no suitable host.%n", vm.getId());
        return false;
    }

    /** Basic cost model combining RAM + Bandwidth + dynamic CPU influence */
    private double calculateHostCost(Host host, double predictedThroughput) {
        double ramCapacityMb = 0;
        double bwCapacity = 0;
        try {
            ramCapacityMb = host.getRam().getCapacity();
        } catch (Exception ignored) {}
        try {
            bwCapacity = host.getBw().getCapacity();
        } catch (Exception ignored) {}

        double ramCost = (ramCapacityMb / 1024.0) * 0.5;                // per GB weight
        double bwCost = (bwCapacity / 1_000_000.0) * 0.2;               // per Gbps weight
        double cpuUtil = host.getCpuMipsUtilization();                  // current dynamic cpu util
        double baseCost = ramCost + bwCost + cpuUtil * 0.3;

        // larger predicted throughput should reduce effective cost (economy of scale)
        return baseCost / (1.0 + predictedThroughput / 10.0);
    }

    /** Print a friendly summary for all allocated VMs */
    private void printAllocationSummary() {
        System.out.println("\n=== Allocation Summary ===");
        for (Map.Entry<Vm, Double> e : vmCostMap.entrySet()) {
            Vm vm = e.getKey();
            double score = e.getValue();
            String hostInfo = "unassigned";
            try {
                Host h = vm.getHost();
                hostInfo = (h != null) ? String.valueOf(h.getId()) : "unassigned";
            } catch (Exception ignored) {}
            double pred = 0.0;
            try {
                pred = (predictor != null) ? predictor.getPredictedThroughput(LocalDateTime.now()) : 0.0;
            } catch (Exception ignored) {}

            System.out.printf("VM %d → Host %s | Score: %.4f | CurrentPredThroughput: %.3f%n",
                    vm.getId(), hostInfo, score, pred);
        }
        System.out.println("==========================\n");
    }

    /**
     * Heuristic estimate for total allocated VM count to decide when to print summary.
     * In some setups it will under/over-estimate; it's just used to trigger summary printing.
     */
    private int getTotalAllocatedVmCountEstimate() {
        // Try to use number of VMs already submitted to the simulation if available:
        int estimate = vmCostMap.size(); // fallback to current size so printing will happen eventually
        try {
            // find total VMs present in hosts (sum)
            int total = 0;
            for (Host h : getHostList()) {
                total += h.getVmList().size();
            }
            if (total > 0) estimate = Math.max(estimate, total);
        } catch (Exception ignored) {
        }
        return estimate;
    }

    public Map<Vm, Double> getVmCostMap() {
        return Collections.unmodifiableMap(vmCostMap);
    }
}
//
//package com.cloudsim.allocater;
//
//import com.cloudsim.WorkloadPredictor;
//import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
//import org.cloudbus.cloudsim.hosts.Host;
//import org.cloudbus.cloudsim.vms.Vm;
//
//import java.time.LocalDateTime;
//import java.util.*;
//
///**
// * FIXED VERSION
// * ✔ Uses the Cloudlet/VM workload timestamp instead of LocalDateTime.now()
// * ✔ Correctly retrieves predicted throughput from LSTM CSV
// * ✔ Removes prediction fallback bug
// */
//
//public class CostAwareAllocater extends VmAllocationPolicySimple {
//
//    private final Map<Vm, Double> vmCostMap = new LinkedHashMap<>();
//    private final WorkloadPredictor predictor;
//
//    public CostAwareAllocater(WorkloadPredictor predictor) {
//        super();
//        this.predictor = predictor;
//    }
//
//    @Override
//    public boolean allocateHostForVm(Vm vm) {
//
//        // 1️⃣ Fetch workload timestamp stored in VM metadata
//        LocalDateTime taskTime = null;
//        try {
//            taskTime = (LocalDateTime) vm.getAttribute("taskTime");
//        } catch (Exception ignored) {}
//
//        double predictedThroughput = 5.0;  // safe fallback
//
//        // 2️⃣ Use the correct timestamp to get the correct predicted throughput
//        if (predictor != null && taskTime != null) {
//            predictedThroughput = predictor.getPredictedThroughput(taskTime);
//        }
//
//        // Add slight jitter for realism
//        predictedThroughput *= (0.95 + Math.random() * 0.1);
//
//        // 3️⃣ Host selection logic
//        Host bestHost = null;
//        double bestScore = Double.MAX_VALUE;
//
//        for (Host host : getHostList()) {
//            if (!host.isSuitableForVm(vm)) continue;
//
//            double utilization = Math.max(0.01, host.getCpuMipsUtilization());
//            double hostCost = calculateHostCost(host, predictedThroughput);
//            double score = utilization * hostCost;
//
//            if (score < bestScore) {
//                bestScore = score;
//                bestHost = host;
//            }
//        }
//
//        // 4️⃣ Allocation attempt
//        if (bestHost != null) {
//            boolean success = super.allocateHostForVm(vm, bestHost);
//
//            if (success) {
//                vmCostMap.put(vm, bestScore);
//
//                System.out.printf(
//                        "🤖 VM %d allocated → Host %d | Score: %.3f | PredThroughput: %.3f (Using timestamp %s)%n",
//                        vm.getId(), bestHost.getId(), bestScore, predictedThroughput, taskTime
//                );
//
//                return true;
//            }
//        }
//
//        System.out.printf("⚠ VM %d could not be allocated — no suitable host.%n", vm.getId());
//        return false;
//    }
//
//    /** Host cost formula */
//    private double calculateHostCost(Host host, double predictedThroughput) {
//        double ramMb = host.getRam().getCapacity();
//        double bw = host.getBw().getCapacity();
//        double cpuUtil = host.getCpuMipsUtilization();
//
//        double ramCost = (ramMb / 1024.0) * 0.5;
//        double bwCost = (bw / 1_000_000.0) * 0.2;
//
//        double baseCost = ramCost + bwCost + cpuUtil * 0.3;
//
//        return baseCost / (1.0 + predictedThroughput / 10.0);
//    }
//
//    public Map<Vm, Double> getVmCostMap() {
//        return Collections.unmodifiableMap(vmCostMap);
//    }
//}