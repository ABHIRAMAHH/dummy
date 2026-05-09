
package com.cloudsim.monitor;

import org.cloudbus.cloudsim.cloudlets.Cloudlet;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

public class MetricsLogger {

    public static class SchedulerResult {
        public double totalCost;
        public double avgCpuUtil;
        public double avgRamUtil;
        public double makespan;
    }

    public static void writeTaskMetrics(String outPath,
                                        List<Cloudlet> cloudlets,
                                        SchedulerResult overall) throws Exception {
        File f = new File(outPath);
        f.getParentFile().mkdirs();

        try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
            pw.println("task_id,vm_id,start_time,finish_time,execution_time,waiting_time,utilization");

            for (Cloudlet c : cloudlets) {
                long id = c.getId();
                String vmId = c.getVm() != null ? String.valueOf(c.getVm().getId()) : "unassigned";

                double start = c.getExecStartTime();
                double finish = c.getFinishTime();
                double exec = c.getActualCpuTime();
                double wait = start - c.getSubmissionDelay();
                double util = exec > 0 ? (c.getTotalLength() / exec) : 0;

                pw.printf("%d,%s,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                        id, vmId, start, finish, exec, wait, util);
            }

            // Overall summary
            pw.println();
            pw.printf("total_cost,avg_cpu_util,avg_ram_util,makespan%n");
            pw.printf("%.4f,%.4f,%.4f,%.2f%n",
                    overall.totalCost, overall.avgCpuUtil, overall.avgRamUtil, overall.makespan);
        }
    }
}