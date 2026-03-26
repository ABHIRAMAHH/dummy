package com.cloudsim.scheduler;

import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;

public class RoundRobinScheduler {

    /** Round-robin by arrival order. */
    public static List<CloudletSimple> schedule(
            List<WorkloadLoader.WorkloadTask> tasks,
            List<Vm> vms,
            DatacenterBrokerSimple broker
    ) {
        List<WorkloadLoader.WorkloadTask> ordered = new ArrayList<>(tasks);
        ordered.sort(Comparator.comparingDouble(WorkloadLoader.WorkloadTask::getArrivalTimeSeconds));

        List<CloudletSimple> cloudlets = new ArrayList<>(tasks.size());
        Map<Long, Integer> bindCounts = new TreeMap<>();

        int idx = 0;
        for (WorkloadLoader.WorkloadTask t : ordered) {
            Vm vm = vms.get(idx % vms.size());
            idx++;

            CloudletSimple c = WorkloadLoader.createCloudletFrom(t);
            double arrival = t.getArrivalTimeSeconds();
            try { c.setSubmissionDelay(arrival / 50); } catch (NoSuchMethodError ignore) {}

            broker.bindCloudletToVm(c, vm);
            c.setVm(vm);

            cloudlets.add(c);
            bindCounts.merge(vm.getId(), 1, Integer::sum);
        }

        System.out.println("Final Bindings " + bindCounts);
        broker.submitCloudletList(cloudlets);
        return cloudlets;
    }
}