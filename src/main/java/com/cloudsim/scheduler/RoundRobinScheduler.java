package com.cloudsim.scheduler;

import com.cloudsim.output.ResultsExporter;
import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;
import static com.cloudsim.scheduler.SchedulerUtil.*;

public class RoundRobinScheduler {

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
        Map<Long, Integer> bindCounts  = new TreeMap<>();
        double[] backlog = new double[V];

        int idx = 0;
        for (WorkloadLoader.WorkloadTask t : ordered) {
            int  j  = idx % V;
            Vm   vm = vms.get(j);
            idx++;

            CloudletSimple c = WorkloadLoader.createCloudletFrom(t);
            double arrival = t.getArrivalTimeSeconds();
            try { c.setSubmissionDelay(arrival / 50); } catch (NoSuchMethodError ignore) {}

            broker.bindCloudletToVm(c, vm);
            c.setVm(vm);
            cloudlets.add(c);
            bindCounts.merge(vm.getId(), 1, Integer::sum);

            // track backlog for display
            double cap  = capacityMiPerSec(vm);
            double exec = lengthMiLike(t) / cap;
            backlog[j] += exec;
        }

        printSummary("RoundRobin", bindCounts, vms, backlog);
        // at the end of each scheduler, after printSummary()
        ResultsExporter.addResult(
                buildResult("RoundRobin", bindCounts, vms, backlog, -1, -1, null)
        );
        broker.submitCloudletList(cloudlets);
        return cloudlets;
    }
}