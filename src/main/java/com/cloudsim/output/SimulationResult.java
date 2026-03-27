package com.cloudsim.output;

import java.util.List;
import java.util.Map;

public class SimulationResult {

    public String simulationRun;
    public int totalTasks;
    public List<SchedulerResult> schedulers;

    public static class SchedulerResult {
        public String name;
        public double fairShare;
        public double loadStdDev;

        // post-simulation metrics (filled by enrichLastResult)
        public double makespan;
        public double avgWaitTime;
        public double totalCost;
        public double totalExecTime;

        // AI only
        public double throughputMin = -1;
        public double throughputMax = -1;
        public Map<String, Double> weights;

        public List<VmResult> vms;
    }

    public static class VmResult {
        public int    id;
        public int    tasks;
        public double mips;
        public double costPerSec;
        public double backlog;

        public VmResult(int id, int tasks, double mips,
                        double costPerSec, double backlog) {
            this.id         = id;
            this.tasks      = tasks;
            this.mips       = mips;
            this.costPerSec = costPerSec;
            this.backlog    = backlog;
        }
    }
}