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
//public class RoundRobinScheduler {
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
//            // Optional: set arrival delay if supported by your CloudSim version
//            try {
//                c.setSubmissionDelay(t.arrivalTime);
//            } catch (NoSuchMethodError e) {
//                // older version – ignore arrival time
//            }
//
//            broker.bindCloudletToVm(c, vm);
//            cloudlets.add(c);
//
//            vmIndex = (vmIndex + 1) % vms.size();
//        }
//
//        // submit all at once
//        broker.submitCloudletList(cloudlets);
//        return cloudlets;
//    }
//}
package com.cloudsim.scheduler;

import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoundRobinScheduler {
    public static Map<Long, Double> taskArrivalMap = new HashMap<>();

    public static List<CloudletSimple> schedule(List<WorkloadLoader.WorkloadTask> tasks,
                                                List<Vm> vms,
                                                DatacenterBrokerSimple broker) {
        // Sort by arrival time derived from Task_Start_Time
        tasks.sort((a, b) -> Double.compare(a.getArrivalTimeSeconds(), b.getArrivalTimeSeconds()));

        int vmIndex = 0;
        List<CloudletSimple> cloudlets = new ArrayList<>();

        for (WorkloadLoader.WorkloadTask t : tasks) {
            CloudletSimple c = WorkloadLoader.createCloudletFrom(t);
            Vm vm = vms.get(vmIndex % vms.size());
            c.setVm(vm);

            // Record arrival mapping for MetricsLogger
            double arrival = t.getArrivalTimeSeconds();
            taskArrivalMap.put(c.getId(), arrival);

            try {
                c.setSubmissionDelay(arrival);
            } catch (NoSuchMethodError ignore) {
            }

            broker.bindCloudletToVm(c, vm);
            cloudlets.add(c);

            vmIndex = (vmIndex + 1) % vms.size();
        }

        broker.submitCloudletList(cloudlets);
        return cloudlets;
    }
}
