package com.cloudsim.scheduler;

import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;

import static com.cloudsim.scheduler.SchedulerUtil.*;

public class CostOnlyGreedyScheduler {

    /**
     * Greedy: for each task (arrival order), choose VM with minimum expected $ cost for that task.
     * Ignores queueing/backlog except via capacity in the cost.
     */
    public static List<CloudletSimple> schedule(
            List<WorkloadLoader.WorkloadTask> tasks,
            List<Vm> vms,
            DatacenterBrokerSimple broker
    ) {
        List<WorkloadLoader.WorkloadTask> ordered = new ArrayList<>(tasks);
        ordered.sort(Comparator.comparingDouble(WorkloadLoader.WorkloadTask::getArrivalTimeSeconds));

        List<CloudletSimple> cloudlets = new ArrayList<>(tasks.size());
        Map<Long, Integer> bindCounts = new TreeMap<>();

        for (WorkloadLoader.WorkloadTask t : ordered) {
            double lengthMi = lengthMiLike(t);

            Vm bestVm = null;
            double bestCost = Double.POSITIVE_INFINITY;

            for (Vm vm : vms) {
                double cap = capacityMiPerSec(vm);
                double execSec = lengthMi / cap;
                double expectedCost = costPerSecond(vm) * execSec;
                if (expectedCost < bestCost) {
                    bestCost = expectedCost;
                    bestVm = vm;
                }
            }
            if (bestVm == null) bestVm = vms.get(0);

            CloudletSimple c = WorkloadLoader.createCloudletFrom(t);
            double arrival = t.getArrivalTimeSeconds();
            try { c.setSubmissionDelay(arrival / 50); } catch (NoSuchMethodError ignore) {}

            broker.bindCloudletToVm(c, bestVm);
            c.setVm(bestVm);

            cloudlets.add(c);
            bindCounts.merge(bestVm.getId(), 1, Integer::sum);
        }

        System.out.println("Final Bindings " + bindCounts);
        broker.submitCloudletList(cloudlets);
        return cloudlets;
    }
}