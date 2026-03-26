
package com.cloudsim.allocater;

import com.cloudsim.WorkloadPredictor;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CostAwareAllocater (fixed for CloudSim time + anti-packing)
 *
 * Fixes:
 *  - Uses simulation clock time (seconds) instead of LocalDateTime.now()
 *  - Adds packing penalty so VMs don't all go to the smallest Host
 *  - Stores predictedThroughput per VM for correct summary printing
 */
public class CostAwareAllocater extends VmAllocationPolicySimple {

    private final Map<Vm, Double> vmScoreMap = new LinkedHashMap<>();
    private final Map<Vm, Double> vmPredMap  = new LinkedHashMap<>();
    private final WorkloadPredictor predictor;

    // Tune this to spread more/less:
    // 0.0 = no spread, 0.25 = moderate spread, 0.5 = strong spread
    private static final double PACKING_PENALTY_PER_VM = 0.25;

    public CostAwareAllocater(WorkloadPredictor predictor) {
        super();
        this.predictor = predictor;
    }

    @Override
    public boolean allocateHostForVm(Vm vm) {
        final List<Host> hostList = getHostList();

        Host bestHost = null;
        double bestScore = Double.MAX_VALUE;

        // Use SIMULATION time (seconds), not wall-clock time.
        double simTime = 0.0;
        try {
            if (getDatacenter() != null && getDatacenter().getSimulation() != null) {
                simTime = getDatacenter().getSimulation().clock();
            }
        } catch (Throwable ignored) {}

        // Get predicted throughput for this allocation moment.
        double predictedThroughput = 5.0; // safe default consistent with your predictor fallback
        try {
            if (predictor != null) {
                predictedThroughput = predictor.getPredictedThroughput(simTime);
            }
        } catch (Throwable ignored) {}

        // Small jitter per-VM to break ties deterministically-ish
        predictedThroughput = predictedThroughput * (0.95 + Math.random() * 0.10); // ±5%

        for (Host host : hostList) {
            if (!host.isSuitableForVm(vm)) continue;

            // At time 0 utilization is often 0 for all hosts; keep tiny floor
            double utilization = 0.01;
            try {
                utilization = Math.max(0.01, host.getCpuMipsUtilization());
            } catch (Throwable ignored) {}

            double hostCost = calculateHostCost(host, predictedThroughput);

            // Packing penalty: prefer spreading VMs across hosts
            int alreadyPlaced = 0;
            try {
                alreadyPlaced = host.getVmList().size();
            } catch (Throwable ignored) {}
            double packingPenalty = 1.0 + PACKING_PENALTY_PER_VM * alreadyPlaced;

            double score = utilization * hostCost * packingPenalty;

            if (score < bestScore) {
                bestScore = score;
                bestHost = host;
            }
        }

        if (bestHost == null) {
            System.out.printf("⚠ VM %d could not be allocated — no suitable host.%n", vm.getId());
            return false;
        }

        boolean success;
        try {
            // CloudSim Plus 6.0.0 should have this signature
            success = super.allocateHostForVm(vm, bestHost);
        } catch (NoSuchMethodError e) {
            // Fallback (just in case)
            success = bestHost.createVm(vm);
        }

        if (!success) {
            System.out.printf("⚠ VM %d allocation failed on Host %d%n", vm.getId(), bestHost.getId());
            return false;
        }

        vmScoreMap.put(vm, bestScore);
        vmPredMap.put(vm, predictedThroughput);

        // Optional feedback hook
        try {
            if (predictor != null) {
                double observedSample = predictedThroughput * (0.5 + Math.random()); // 0.5x..1.5x
                predictor.updateObservedThroughput(observedSample);
            }
        } catch (Throwable ignored) {}

        System.out.printf(
                "🤖 VM %d allocated → Host %d | Score: %.3f | PredThroughput: %.3f%n",
                vm.getId(),
                bestHost.getId(),
                bestScore,
                predictedThroughput
        );

        // Print summary every time (simple + correct).
        // If you prefer: only print when all VMs are allocated, you can add your own condition.
        printAllocationSummary();

        return true;
    }

    /** Basic cost model combining RAM + Bandwidth + dynamic CPU influence */
    private double calculateHostCost(Host host, double predictedThroughput) {
        double ramCapacityMb = 0;
        double bwCapacity = 0;

        try { ramCapacityMb = host.getRam().getCapacity(); } catch (Throwable ignored) {}
        try { bwCapacity = host.getBw().getCapacity(); } catch (Throwable ignored) {}

        double ramCost = (ramCapacityMb / 1024.0) * 0.5;     // per-GB weight
        double bwCost  = (bwCapacity / 1_000_000.0) * 0.2;   // per-Gbps-ish weight

        double cpuUtil = 0.0;
        try { cpuUtil = host.getCpuMipsUtilization(); } catch (Throwable ignored) {}

        double baseCost = ramCost + bwCost + cpuUtil * 0.3;

        // Higher predicted throughput => lower effective cost
        return baseCost / (1.0 + predictedThroughput / 10.0);
    }

    /** Print a friendly summary for all allocated VMs */
    private void printAllocationSummary() {
        System.out.println("\n=== Allocation Summary ===");
        for (Map.Entry<Vm, Double> e : vmScoreMap.entrySet()) {
            Vm vm = e.getKey();
            double score = e.getValue();

            String hostInfo = "unassigned";
            try {
                Host h = vm.getHost();
                hostInfo = (h != null) ? String.valueOf(h.getId()) : "unassigned";
            } catch (Throwable ignored) {}

            double predUsed = vmPredMap.getOrDefault(vm, 0.0);

            System.out.printf(
                    "VM %d → Host %s | Score: %.4f | PredThroughputUsed: %.3f%n",
                    vm.getId(), hostInfo, score, predUsed
            );
        }
        System.out.println("==========================\n");
    }

    public Map<Vm, Double> getVmCostMap() {
        return Collections.unmodifiableMap(vmScoreMap);
    }
}