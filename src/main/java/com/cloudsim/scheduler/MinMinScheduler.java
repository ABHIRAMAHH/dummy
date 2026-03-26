package com.cloudsim.scheduler;

import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;

import static com.cloudsim.scheduler.SchedulerUtil.*;

public class MinMinScheduler {

    /**
     * Classic Min-Min heuristic:
     * Repeatedly pick the task that has the minimum earliest completion time (ECT) across all tasks,
     * assign it to the VM that gives that ECT, update that VM ready time, repeat.
     *
     * NOTE: This is offline scheduling (looks at all tasks together).
     * We still preserve submissionDelay(arrival) for your workload model.
     */
    public static List<CloudletSimple> schedule(
            List<WorkloadLoader.WorkloadTask> tasks,
            List<Vm> vms,
            DatacenterBrokerSimple broker
    ) {
        List<WorkloadLoader.WorkloadTask> remaining = new ArrayList<>(tasks);

        // VM "ready time" in seconds
        Map<Vm, Double> readyTime = new HashMap<>();
        for (Vm vm : vms) readyTime.put(vm, 0.0);

        List<CloudletSimple> cloudlets = new ArrayList<>(tasks.size());
        Map<Long, Integer> bindCounts = new TreeMap<>();

        while (!remaining.isEmpty()) {
            WorkloadLoader.WorkloadTask bestTask = null;
            Vm bestVm = null;
            double bestECT = Double.POSITIVE_INFINITY;

            // Find task with minimum ECT
            for (WorkloadLoader.WorkloadTask t : remaining) {
                double arrival = t.getArrivalTimeSeconds();
                double lengthMi = lengthMiLike(t);

                Vm localBestVm = null;
                double localBestECT = Double.POSITIVE_INFINITY;

                for (Vm vm : vms) {
                    double cap = capacityMiPerSec(vm);
                    double exec = lengthMi / cap;

                    double start = Math.max(arrival, readyTime.getOrDefault(vm, 0.0));
                    double ect = start + exec;

                    if (ect < localBestECT) {
                        localBestECT = ect;
                        localBestVm = vm;
                    }
                }

                if (localBestECT < bestECT) {
                    bestECT = localBestECT;
                    bestTask = t;
                    bestVm = localBestVm;
                }
            }

            // Assign bestTask -> bestVm
            CloudletSimple c = WorkloadLoader.createCloudletFrom(bestTask);
            double arrival = bestTask.getArrivalTimeSeconds();
            try { c.setSubmissionDelay(arrival / 50); } catch (NoSuchMethodError ignore) {}

            broker.bindCloudletToVm(c, bestVm);
            c.setVm(bestVm);
            cloudlets.add(c);
            bindCounts.merge(bestVm.getId(), 1, Integer::sum);

            // update ready time
            double exec = lengthMiLike(bestTask) / capacityMiPerSec(bestVm);
            double start = Math.max(arrival, readyTime.getOrDefault(bestVm, 0.0));
            readyTime.put(bestVm, start + exec);

            remaining.remove(bestTask);
        }

        System.out.println("Final Bindings " + bindCounts);
        broker.submitCloudletList(cloudlets);
        return cloudlets;
    }
}