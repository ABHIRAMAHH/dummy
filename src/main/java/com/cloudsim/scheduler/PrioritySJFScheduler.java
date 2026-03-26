package com.cloudsim.scheduler;

import com.cloudsim.WorkloadPredictor;
import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.ArrayList;
import java.util.List;

public class PrioritySJFScheduler {

    public static List<CloudletSimple> schedule(
            List<WorkloadLoader.WorkloadTask> tasks,
            List<Vm> vms,
            DatacenterBrokerSimple broker,
            WorkloadPredictor predictor
    ) {
        tasks.sort((a, b) -> {
            int pa = SchedulerUtil.priorityValue(a.jobPriority);
            int pb = SchedulerUtil.priorityValue(b.jobPriority);
            if (pa != pb) return Integer.compare(pb, pa);
            return Double.compare(a.taskExecutionTime, b.taskExecutionTime);
        });

        List<CloudletSimple> cloudlets = new ArrayList<>();
        int vmIndex = 0;

        for (var t : tasks) {
            CloudletSimple c = WorkloadLoader.createCloudletFrom(t);
            double arrival = t.getArrivalTimeSeconds();
            try { c.setSubmissionDelay(arrival / 50); } catch (NoSuchMethodError ignore) {}

            Vm vm = vms.get(vmIndex % vms.size());
            broker.bindCloudletToVm(c, vm);
            c.setVm(vm);

            cloudlets.add(c);
            vmIndex++;
        }

        broker.submitCloudletList(cloudlets);
        return cloudlets;
    }
}