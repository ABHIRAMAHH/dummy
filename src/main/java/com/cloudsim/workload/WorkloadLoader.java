
package com.cloudsim.workload;

import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/*
New CSV format:
Task_Start_Time,CPU_Utilization (%),Memory_Consumption (MB),
Task_Execution_Time (ms),System_Throughput (tasks/sec),
Number_of_Active_Users,Network_Bandwidth_Utilization (Mbps),
Job_Priority,Scheduler_Type,Resource_Allocation_Type
*/

public class WorkloadLoader {

    public static class WorkloadTask {
        public long taskId;
        public double arrivalSeconds;
        public LocalDateTime startTime;
        public double cpuUtilization;
        public double memoryConsumption;
        public double taskExecutionTime;
        public double systemThroughput;
        public double numberOfActiveUsers;
        public double networkBandwidthUtilization;
        public String jobPriority;
        public String schedulerType;
        public String resourceAllocationType;

        public double getArrivalTimeSeconds() {
//            return (startTime.getMinute() * 60.0) + startTime.getSecond();
            return arrivalSeconds;
        }
    }

    /** ✅ Existing load(String path) version (unchanged) */
    public static List<WorkloadTask> load(String csvPath) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            return parseCsv(br);
        }
    }

    /** ✅ NEW: Overload that loads from InputStream (for resources folder use) */
    public static List<WorkloadTask> load(InputStream inputStream) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            return parseCsv(br);
        }
    }

    /** ✅ Shared parsing logic */
    private static List<WorkloadTask> parseCsv(BufferedReader br) throws Exception {
        List<WorkloadTask> tasks = new ArrayList<>();

        // Your raw dataset uses "dd-MM-yyyy HH:mm"
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

        String line;
        boolean first = true;
        long idCounter = 1;

        LocalDateTime baseTime = null;

        while ((line = br.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            if (first && line.toLowerCase().contains("task_start_time")) {
                first = false;
                continue;
            }

            String[] parts = line.split(",");
            if (parts.length < 10) continue;

            WorkloadTask t = new WorkloadTask();
            t.taskId = idCounter++;
            t.startTime = LocalDateTime.parse(parts[0].trim(), formatter);

            if (baseTime == null) baseTime = t.startTime;

            t.arrivalSeconds = Duration.between(baseTime, t.startTime).toSeconds() / 10.0;
//            t.arrivalSeconds=0;
            t.cpuUtilization = Double.parseDouble(parts[1].trim());
            t.memoryConsumption = Double.parseDouble(parts[2].trim());
            t.taskExecutionTime = Double.parseDouble(parts[3].trim());
            t.systemThroughput = Double.parseDouble(parts[4].trim());
            t.numberOfActiveUsers = Double.parseDouble(parts[5].trim());
            t.networkBandwidthUtilization = Double.parseDouble(parts[6].trim());
            t.jobPriority = parts[7].trim();
            t.schedulerType = parts[8].trim();
            t.resourceAllocationType = parts[9].trim();

            tasks.add(t);
        }
        return tasks;
    }
    public static CloudletSimple createCloudletFrom(WorkloadTask t) {

        long length = Math.max(100, (long) ((t.taskExecutionTime * t.cpuUtilization) / 50));

        CloudletSimple cloudlet = new CloudletSimple(length, 1, new UtilizationModelFull());

        cloudlet.setId(t.taskId);
        cloudlet.setFileSize(100);
        cloudlet.setOutputSize(100);

        CloudletMetadata.storeMetadata(cloudlet.getId(), t);

        return cloudlet;
    }
}

