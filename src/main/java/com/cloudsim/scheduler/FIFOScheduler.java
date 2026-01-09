//package com.cloudsim.scheduler;
//
//import com.cloudsim.workload.WorkloadLoader;
//import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
//import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
//import org.cloudbus.cloudsim.vms.Vm;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.ArrayList;
//import java.util.List;
//
//public class FIFOScheduler {
//    public static Map<Long, Double> taskArrivalMap = new HashMap<>();
//    public static List<CloudletSimple> schedule(List<WorkloadLoader.WorkloadTask> tasks,
//                                                List<Vm> vms,
//                                                DatacenterBrokerSimple broker) {
//        tasks.sort((a, b) -> Double.compare(a.arrivalTime, b.arrivalTime));
//        int vmIndex = 0;
//        List<CloudletSimple> cloudlets = new ArrayList<>();
//
//        for (WorkloadLoader.WorkloadTask t : tasks) {
//            CloudletSimple c = WorkloadLoader.createCloudletFrom(t);
//            Vm vm = vms.get(vmIndex);
//            c.setVm(vm);
//
//            try {
//                c.setSubmissionDelay(t.arrivalTime);
//            } catch (NoSuchMethodError e) {
//                // ignore if unsupported
//            }
//
//            broker.bindCloudletToVm(c, vm);
//            cloudlets.add(c);
//            vmIndex = (vmIndex + 1) % vms.size();
//        }
//
//        broker.submitCloudletList(cloudlets);
//        return cloudlets;
//    }
//}
package com.cloudsim.scheduler;

import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;

public class FIFOScheduler {
    public static Map<Long, Double> taskArrivalMap = new HashMap<>();

    private static int priorityValue(String p) {
        if (p == null) return 1;
        String s = p.trim().toLowerCase();
        switch (s) {
            case "high":
                return 3;
            case "medium":
                return 2;
            case "low":
                return 1;
            default:
                try {
                    return Integer.parseInt(s);
                } catch (Exception e) {
                    return 1;
                }
        }
    }

    public static List<CloudletSimple> schedule(List<WorkloadLoader.WorkloadTask> tasks,
                                                List<Vm> vms,
                                                DatacenterBrokerSimple broker) {
        // Sort by Job Priority → then by arrival time
        tasks.sort((a, b) -> {
            int pa = priorityValue(a.jobPriority);
            int pb = priorityValue(b.jobPriority);
            if (pa != pb) return Integer.compare(pb, pa);
            return a.startTime.compareTo(b.startTime);
        });

        List<CloudletSimple> cloudlets = new ArrayList<>();
        int vmIndex = 0;

        for (WorkloadLoader.WorkloadTask t : tasks) {
            CloudletSimple c = WorkloadLoader.createCloudletFrom(t);

            // Choose VM in round fashion for now
            Vm vm = vms.get(vmIndex % vms.size());
            c.setVm(vm);

            // Keep track of arrival time for metrics
            double arrivalSeconds = t.getArrivalTimeSeconds();
            taskArrivalMap.put(c.getId(), arrivalSeconds);

            try {
                c.setSubmissionDelay(arrivalSeconds);
            } catch (NoSuchMethodError ignore) {
            }

            broker.bindCloudletToVm(c, vm);
            cloudlets.add(c);
            vmIndex++;
        }

        broker.submitCloudletList(cloudlets);
        return cloudlets;
    }
}