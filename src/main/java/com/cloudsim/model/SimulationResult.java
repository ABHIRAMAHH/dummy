//package com.cloudsim.model;
//
//import java.util.Map;
//
//public class SimulationResult {
//
//    public Map<Long, Integer> vmBindings;
//    public double totalCost;
//    public double makespan;
//    public double avgRamUtil;
//
//    public SimulationResult(Map<Long, Integer> vmBindings,
//                            double totalCost,
//                            double makespan,
//                            double avgRamUtil) {
//        this.vmBindings = vmBindings;
//        this.totalCost = totalCost;
//        this.makespan = makespan;
//        this.avgRamUtil = avgRamUtil;
//    }
//}
package com.cloudsim.model;

import java.util.Map;

public class SimulationResult {

    public Map<Long, Integer> vmBindings;
    public double totalCost;
    public double makespan;
    public double avgRamUtil;

    // ── New fields for paper metrics ──
    public double avgWaitTime;
    public double avgTurnaroundTime;
    public double loadImbalance;      // std dev of VM task counts (lower = better)
    public double costEfficiency;     // tasks/dollar
    public String schedulerName;

    public SimulationResult(Map<Long, Integer> vmBindings,
                            double totalCost,
                            double makespan,
                            double avgRamUtil) {
        this.vmBindings = vmBindings;
        this.totalCost  = totalCost;
        this.makespan   = makespan;
        this.avgRamUtil = avgRamUtil;
    }

    // Full constructor
    public SimulationResult(String schedulerName,
                            Map<Long, Integer> vmBindings,
                            double totalCost,
                            double makespan,
                            double avgRamUtil,
                            double avgWaitTime,
                            double avgTurnaroundTime,
                            double loadImbalance,
                            double costEfficiency) {
        this.schedulerName      = schedulerName;
        this.vmBindings         = vmBindings;
        this.totalCost          = totalCost;
        this.makespan           = makespan;
        this.avgRamUtil         = avgRamUtil;
        this.avgWaitTime        = avgWaitTime;
        this.avgTurnaroundTime  = avgTurnaroundTime;
        this.loadImbalance      = loadImbalance;
        this.costEfficiency     = costEfficiency;
    }
}