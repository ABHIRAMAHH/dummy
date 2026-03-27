package com.cloudsim.scheduler;

import com.cloudsim.output.ResultsExporter;
import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;
import static com.cloudsim.scheduler.SchedulerUtil.*;

public class SJFScheduler {

    public static List<CloudletSimple> schedule(
            List<WorkloadLoader.WorkloadTask> tasks,
            List<Vm> vms,
            DatacenterBrokerSimple broker
    ) {
        int V = vms.size();
        double refCap = vms.stream()
                .mapToDouble(SchedulerUtil::capacityMiPerSec).max().orElse(1.0);

        List<WorkloadLoader.WorkloadTask> ordered = new ArrayList<>(tasks);
        ordered.sort(Comparator
                .comparingDouble((WorkloadLoader.WorkloadTask t) -> lengthMiLike(t) / refCap)
                .thenComparingDouble(WorkloadLoader.WorkloadTask::getArrivalTimeSeconds));

        Map<Vm, Double> readyTime  = new HashMap<>();
        for (Vm vm : vms) readyTime.put(vm, 0.0);

        List<CloudletSimple> cloudlets = new ArrayList<>(tasks.size());
        Map<Long, Integer>   bindCounts = new TreeMap<>();
        double[] backlog = new double[V];

        for (WorkloadLoader.WorkloadTask t : ordered) {
            double arrival  = t.getArrivalTimeSeconds();
            double lengthMi = lengthMiLike(t);

            Vm     bestVm  = null;
            double bestECT = Double.POSITIVE_INFINITY;
            int    bestIdx = 0;

            for (int j = 0; j < V; j++) {
                Vm     vm   = vms.get(j);
                double cap  = capacityMiPerSec(vm);
                double exec = lengthMi / cap;
                double start = Math.max(arrival, readyTime.getOrDefault(vm, 0.0));
                double ect   = start + exec;
                if (ect < bestECT) { bestECT = ect; bestVm = vm; bestIdx = j; }
            }
            if (bestVm == null) { bestVm = vms.get(0); bestIdx = 0; }

            CloudletSimple c = WorkloadLoader.createCloudletFrom(t);
            try { c.setSubmissionDelay(arrival / 50); } catch (NoSuchMethodError ignore) {}
            broker.bindCloudletToVm(c, bestVm);
            c.setVm(bestVm);
            cloudlets.add(c);
            bindCounts.merge(bestVm.getId(), 1, Integer::sum);

            double cap   = capacityMiPerSec(bestVm);
            double exec  = lengthMi / cap;
            double start = Math.max(arrival, readyTime.getOrDefault(bestVm, 0.0));
            readyTime.put(bestVm, start + exec);
            backlog[bestIdx] += exec;
        }

        printSummary("SJF", bindCounts, vms, backlog);
        // at the end of each scheduler, after printSummary()
        ResultsExporter.addResult(
                buildResult("SJF", bindCounts, vms, backlog, -1, -1, null)
        );
        broker.submitCloudletList(cloudlets);
        return cloudlets;
    }
}