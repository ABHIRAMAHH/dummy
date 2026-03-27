package com.cloudsim.scheduler;

import com.cloudsim.output.ResultsExporter;
import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;
import static com.cloudsim.scheduler.SchedulerUtil.*;

public class MinMinScheduler {

    public static List<CloudletSimple> schedule(
            List<WorkloadLoader.WorkloadTask> tasks,
            List<Vm> vms,
            DatacenterBrokerSimple broker
    ) {
        int V = vms.size();

        List<WorkloadLoader.WorkloadTask> remaining = new ArrayList<>(tasks);
        Map<Vm, Double> readyTime  = new HashMap<>();
        for (Vm vm : vms) readyTime.put(vm, 0.0);

        List<CloudletSimple> cloudlets = new ArrayList<>(tasks.size());
        Map<Long, Integer>   bindCounts = new TreeMap<>();
        double[] backlog = new double[V];

        while (!remaining.isEmpty()) {
            WorkloadLoader.WorkloadTask bestTask = null;
            Vm     bestVm  = null;
            double bestECT = Double.POSITIVE_INFINITY;
            int    bestIdx = 0;

            for (WorkloadLoader.WorkloadTask t : remaining) {
                double arrival  = t.getArrivalTimeSeconds();
                double lengthMi = lengthMiLike(t);

                for (int j = 0; j < V; j++) {
                    Vm     vm   = vms.get(j);
                    double cap  = capacityMiPerSec(vm);
                    double exec = lengthMi / cap;
                    double start = Math.max(arrival, readyTime.getOrDefault(vm, 0.0));
                    double ect   = start + exec;
                    if (ect < bestECT) {
                        bestECT  = ect;
                        bestTask = t;
                        bestVm   = vm;
                        bestIdx  = j;
                    }
                }
            }

            CloudletSimple c = WorkloadLoader.createCloudletFrom(bestTask);
            double arrival = bestTask.getArrivalTimeSeconds();
            try { c.setSubmissionDelay(arrival / 50); } catch (NoSuchMethodError ignore) {}
            broker.bindCloudletToVm(c, bestVm);
            c.setVm(bestVm);
            cloudlets.add(c);
            bindCounts.merge(bestVm.getId(), 1, Integer::sum);

            double cap   = capacityMiPerSec(bestVm);
            double exec  = lengthMiLike(bestTask) / cap;
            double start = Math.max(arrival, readyTime.getOrDefault(bestVm, 0.0));
            readyTime.put(bestVm, start + exec);
            backlog[bestIdx] += exec;

            remaining.remove(bestTask);
        }

        printSummary("MinMin", bindCounts, vms, backlog);
        // at the end of each scheduler, after printSummary()
        ResultsExporter.addResult(
                buildResult("MinMin", bindCounts, vms, backlog, -1, -1, null)
        );
        broker.submitCloudletList(cloudlets);
        return cloudlets;
    }
}