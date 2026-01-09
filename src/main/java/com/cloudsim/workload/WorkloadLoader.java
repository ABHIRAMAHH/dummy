//package com.cloudsim.workload;
//
//
//import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
//import org.cloudbus.cloudsim.core.CloudSim;
//import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
//
//import java.io.BufferedReader;
//import java.io.FileReader;
//import java.util.ArrayList;
//import java.util.List;
//
///*
//CSV format:
//task_id,arrival_time,length,ram_req,bw_req,deadline
//*/
//public class WorkloadLoader {
//    public static class WorkloadTask {
//        public int taskId;
//        public double arrivalTime;
//        public long length;
//        public int ramReq;
//        public long bwReq;
//        public double deadline;
//    }
//
//    public static List<WorkloadTask> load(String csvPath) throws Exception {
//        List<WorkloadTask> tasks = new ArrayList<>();
//        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
//            String line;
//            boolean first=true;
//            while ((line = br.readLine()) != null) {
//                if (line.trim().isEmpty()) continue;
//                if (first && line.toLowerCase().contains("task_id")) { first=false; continue; }
//                String[] parts = line.split(",");
//                WorkloadTask t = new WorkloadTask();
//                t.taskId = Integer.parseInt(parts[0].trim());
//                t.arrivalTime = Double.parseDouble(parts[1].trim());
//                t.length = Long.parseLong(parts[2].trim());
//                t.ramReq = Integer.parseInt(parts[3].trim());
//                t.bwReq = Long.parseLong(parts[4].trim());
//                if (parts.length>5) t.deadline = Double.parseDouble(parts[5].trim());
//                else t.deadline = Double.MAX_VALUE;
//                tasks.add(t);
//            }
//        }
//        return tasks;
//    }
//
//    // helper returning CloudletSimple instances (create cloudlets later when assigning to VMs)
//    public static CloudletSimple createCloudletFrom(WorkloadTask t) {
//        CloudletSimple c = new CloudletSimple(t.length, 1, new UtilizationModelFull());
//        c.setId(t.taskId);
//
//        // You can set realistic I/O sizes if desired
//        c.setFileSize(300);
//        c.setOutputSize(300);
//
//        // Optionally attach task metadata (RAM/BW requirements)
//        // CloudSim allows adding custom attributes via setAttribute()
//        CloudletMetadata.storeMetadata(c.getId(), t);
//
//        return c;
//    }
//}
package com.cloudsim.workload;

import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
            return (startTime.getMinute() * 60.0) + startTime.getSecond();
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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");


        String line;
        boolean first = true;
        long idCounter = 1;

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
        long length = Math.max(1000, (long) (t.taskExecutionTime * t.cpuUtilization));

        CloudletSimple cloudlet = new CloudletSimple(length, 1, new UtilizationModelFull());
        cloudlet.setId(t.taskId);
        cloudlet.setFileSize(300);
        cloudlet.setOutputSize(300);
//        cloudlet.setRam(1024)
        CloudletMetadata.storeMetadata(cloudlet.getId(), t);
        return cloudlet;
    }
}

