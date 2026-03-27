package com.cloudsim.scheduler;

import com.cloudsim.WorkloadPredictor;
import com.cloudsim.output.ResultsExporter;
import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;
import static com.cloudsim.scheduler.SchedulerUtil.*;

public class PrioritySJFScheduler {

    public static List<CloudletSimple> schedule(
            List<WorkloadLoader.WorkloadTask> tasks,
            List<Vm> vms,
            DatacenterBrokerSimple broker,
            WorkloadPredictor predictor
    ) {
        int V = vms.size();

        // Sort: highest priority first, then shortest job first (SJF tie-break)
        tasks.sort((a, b) -> {
            int pa = priorityValue(a.jobPriority);
            int pb = priorityValue(b.jobPriority);
            if (pa != pb) return Integer.compare(pb, pa);
            return Double.compare(a.taskExecutionTime, b.taskExecutionTime);
        });

        List<CloudletSimple> cloudlets = new ArrayList<>();
        Map<Long, Integer>   bindCounts = new TreeMap<>();
        double[] backlog = new double[V];

        int vmIndex = 0;

        for (WorkloadLoader.WorkloadTask t : tasks) {
            CloudletSimple c      = WorkloadLoader.createCloudletFrom(t);
            double         arrival = t.getArrivalTimeSeconds();
            try { c.setSubmissionDelay(arrival / 50); } catch (NoSuchMethodError ignore) {}

            int j  = vmIndex % V;
            Vm  vm = vms.get(j);
            vmIndex++;

            broker.bindCloudletToVm(c, vm);
            c.setVm(vm);
            cloudlets.add(c);
            bindCounts.merge(vm.getId(), 1, Integer::sum);

            // track backlog for display
            backlog[j] += lengthMiLike(t) / capacityMiPerSec(vm);
        }

        printSummary("PrioritySJF", bindCounts, vms, backlog);
        // at the end of each scheduler, after printSummary()
        ResultsExporter.addResult(
                buildResult("PrioritySJF", bindCounts, vms, backlog, -1, -1, null)
        );
        broker.submitCloudletList(cloudlets);
        return cloudlets;
    }
}