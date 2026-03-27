package com.cloudsim.scheduler;

import com.cloudsim.output.ResultsExporter;
import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;
import static com.cloudsim.scheduler.SchedulerUtil.*;

public class CostOnlyGreedyScheduler {

    public static List<CloudletSimple> schedule(
            List<WorkloadLoader.WorkloadTask> tasks,
            List<Vm> vms,
            DatacenterBrokerSimple broker
    ) {
        int V = vms.size();

        List<WorkloadLoader.WorkloadTask> ordered = new ArrayList<>(tasks);
        ordered.sort(Comparator.comparingDouble(
                WorkloadLoader.WorkloadTask::getArrivalTimeSeconds));

        List<CloudletSimple> cloudlets = new ArrayList<>(tasks.size());
        Map<Long, Integer>   bindCounts = new TreeMap<>();
        double[] backlog = new double[V];

        for (WorkloadLoader.WorkloadTask t : ordered) {
            double lengthMi = lengthMiLike(t);

            Vm     bestVm   = null;
            double bestCost = Double.POSITIVE_INFINITY;
            int    bestIdx  = 0;

            for (int j = 0; j < V; j++) {
                Vm     vm       = vms.get(j);
                double cap      = capacityMiPerSec(vm);
                double execSec  = lengthMi / cap;
                double cost     = costPerSecond(vm) * execSec;
                if (cost < bestCost) { bestCost = cost; bestVm = vm; bestIdx = j; }
            }
            if (bestVm == null) { bestVm = vms.get(0); bestIdx = 0; }

            CloudletSimple c = WorkloadLoader.createCloudletFrom(t);
            double arrival = t.getArrivalTimeSeconds();
            try { c.setSubmissionDelay(arrival / 50); } catch (NoSuchMethodError ignore) {}
            broker.bindCloudletToVm(c, bestVm);
            c.setVm(bestVm);
            cloudlets.add(c);
            bindCounts.merge(bestVm.getId(), 1, Integer::sum);

            backlog[bestIdx] += lengthMi / capacityMiPerSec(bestVm);
        }

        printSummary("CostOnlyGreedy", bindCounts, vms, backlog);
        // at the end of each scheduler, after printSummary()
        ResultsExporter.addResult(
                buildResult("CostOnlyGreedy", bindCounts, vms, backlog, -1, -1, null)
        );
        broker.submitCloudletList(cloudlets);
        return cloudlets;
    }
}