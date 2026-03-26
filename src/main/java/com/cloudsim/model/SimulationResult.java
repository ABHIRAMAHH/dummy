package com.cloudsim.model;

import java.util.Map;

public class SimulationResult {

    public Map<Long, Integer> vmBindings;
    public double totalCost;
    public double makespan;
    public double avgRamUtil;

    public SimulationResult(Map<Long, Integer> vmBindings,
                            double totalCost,
                            double makespan,
                            double avgRamUtil) {
        this.vmBindings = vmBindings;
        this.totalCost = totalCost;
        this.makespan = makespan;
        this.avgRamUtil = avgRamUtil;
    }
}