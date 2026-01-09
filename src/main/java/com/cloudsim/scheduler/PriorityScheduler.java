package com.cloudsim.scheduler;

import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;

public class PriorityScheduler {

    public static Map<Long, Double> taskArrivalMap = new HashMap<>();

    /** Convert jobPriority string → integer for comparison */
    private static int priorityValue(String p) {
        if (p == null) return 1;

        String s = p.trim().toLowerCase();

        switch (s) {
            case "high": return 3;
            case "medium": return 2;
            case "low": return 1;
            default:
                try {
                    return Integer.parseInt(s);
                } catch (Exception e) {
                    return 1; // default low priority
                }
        }
    }

    /**
     * PRIORITY SCHEDULER:
     * 1. Sort by priority (desc)
     * 2. If equal → earlier start time first
     * 3. Use round-robin to assign VMs
     */
    public static List<CloudletSimple> schedule(List<WorkloadLoader.WorkloadTask> tasks,
                                                List<Vm> vms,
                                                DatacenterBrokerSimple broker) {

        // Step 1: Sort tasks by priority + arrival time
        tasks.sort((a, b) -> {
            int pa = priorityValue(a.jobPriority);
            int pb = priorityValue(b.jobPriority);

            if (pa != pb) {
                return Integer.compare(pb, pa);  // Higher priority first
            }
            return a.startTime.compareTo(b.startTime);  // FIFO for same priority
        });

        List<CloudletSimple> cloudlets = new ArrayList<>();
        int vmIndex = 0;

        // Step 2: Create Cloudlets & assign to VMs
        for (WorkloadLoader.WorkloadTask t : tasks) {

            CloudletSimple c = WorkloadLoader.createCloudletFrom(t);

            Vm vm = vms.get(vmIndex % vms.size());
            c.setVm(vm);

            double arrival = t.getArrivalTimeSeconds();
            taskArrivalMap.put(c.getId(), arrival);

            try {
                c.setSubmissionDelay(arrival);
            } catch (NoSuchMethodError ignore) {}

            broker.bindCloudletToVm(c, vm);
            cloudlets.add(c);

            vmIndex++;
        }

        // Step 3: Submit all Cloudlets at once
        broker.submitCloudletList(cloudlets);

        return cloudlets;
    }
}
