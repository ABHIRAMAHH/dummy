
package com.cloudsim.scheduler;

import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.vms.Vm;

public final class SchedulerUtil {
    private SchedulerUtil() {}

    public static int priorityValue(String p) {
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
    public static double lengthMiLike(WorkloadLoader.WorkloadTask t) {
        return Math.max(1000.0, t.taskExecutionTime * t.cpuUtilization);
    }

    /** VM capacity in MI/sec-ish (approx): mips * pes */
    public static double capacityMiPerSec(Vm vm) {
        return Math.max(1.0, vm.getMips() * vm.getNumberOfPes());
    }

    /** Safe cost/second getter for your CostVmSimple */
    public static double costPerSecond(Vm vm) {
        try {
            if (vm instanceof com.cloudsim.CostVmSimple) {
                double c = ((com.cloudsim.CostVmSimple) vm).getCostPerSecond();
                return (c > 0.0) ? c : 0.01;
            }
        } catch (Throwable ignored) {}
        return 0.01;
    }
}