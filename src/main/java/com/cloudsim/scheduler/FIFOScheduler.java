package com.cloudsim.scheduler;

import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;

import static com.cloudsim.scheduler.SchedulerUtil.*;

public class FIFOScheduler {

    /** FIFO by arrival time; bind each task to VM that yields earliest completion time. */
    public static List<CloudletSimple> schedule(
            List<WorkloadLoader.WorkloadTask> tasks,
            List<Vm> vms,
            DatacenterBrokerSimple broker
    ) {
        List<WorkloadLoader.WorkloadTask> ordered = new ArrayList<>(tasks);
        ordered.sort(Comparator.comparingDouble(WorkloadLoader.WorkloadTask::getArrivalTimeSeconds));

        Map<Vm, Double> readyTime = new HashMap<>();
        for (Vm vm : vms) readyTime.put(vm, 0.0);

        List<CloudletSimple> cloudlets = new ArrayList<>(tasks.size());
        Map<Long, Integer> bindCounts = new TreeMap<>();

        for (WorkloadLoader.WorkloadTask t : ordered) {
            double arrival = t.getArrivalTimeSeconds();
            double lengthMi = lengthMiLike(t);

            Vm bestVm = null;
            double bestECT = Double.POSITIVE_INFINITY;

            for (Vm vm : vms) {
                double cap = capacityMiPerSec(vm);
                double exec = lengthMi / cap;
                double start = Math.max(arrival, readyTime.getOrDefault(vm, 0.0));
                double ect = start + exec;
                if (ect < bestECT) {
                    bestECT = ect;
                    bestVm = vm;
                }
            }
            if (bestVm == null) bestVm = vms.get(0);

            CloudletSimple c = WorkloadLoader.createCloudletFrom(t);
            try { c.setSubmissionDelay(arrival / 50); } catch (NoSuchMethodError ignore) {}
            broker.bindCloudletToVm(c, bestVm);
            c.setVm(bestVm);

            cloudlets.add(c);
            bindCounts.merge(bestVm.getId(), 1, Integer::sum);

            double exec = lengthMi / capacityMiPerSec(bestVm);
            double start = Math.max(arrival, readyTime.getOrDefault(bestVm, 0.0));
            readyTime.put(bestVm, start + exec);
        }

        System.out.println("Final Bindings " + bindCounts);
        broker.submitCloudletList(cloudlets);
        return cloudlets;
    }
}