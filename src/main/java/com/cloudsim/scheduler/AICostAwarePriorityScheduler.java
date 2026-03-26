package com.cloudsim.scheduler;

import com.cloudsim.WorkloadPredictor;
import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;

import static com.cloudsim.scheduler.SchedulerUtil.*;

public class AICostAwarePriorityScheduler {

    /**
     * AI-ish cost-aware + priority scheduler:
     * - Sort: priority desc, arrival asc
     * - Backlog/capacity queue model per VM
     * - Objective in "dollars" to avoid mixing units:
     *      objective = expectedCost + (completionTimeSeconds * dollarsPerSecondLatency)
     * - Uses predictor to adjust effective service rate (capacity) OR cost. Here we adjust capacity.
     */
    public static List<CloudletSimple> schedule(
            List<WorkloadLoader.WorkloadTask> tasks,
            List<Vm> vms,
            DatacenterBrokerSimple broker,
            WorkloadPredictor predictor
    ) {
        // Sort by priority desc, then arrival asc
        tasks.sort((a, b) -> {
            int pa = priorityValue(a.jobPriority);
            int pb = priorityValue(b.jobPriority);
            if (pa != pb) return Integer.compare(pb, pa);
            return Double.compare(a.getArrivalTimeSeconds(), b.getArrivalTimeSeconds());
        });

        // backlog model in MI-like units
        Map<Vm, Double> backlogMi = new HashMap<>();
        Map<Vm, Double> lastUpd = new HashMap<>();
        for (Vm vm : vms) {
            backlogMi.put(vm, 0.0);
            lastUpd.put(vm, 0.0);
        }

        final double EPS = 1e-9;
        final int ROTATE_EVERY_TASKS = 25;
        final double dollarsPerSecondLatency = 0.02; // tune: how much you "pay" per second of completion time
        final double predictorWeight = 0.30;          // 0..1 influence (higher => predictor matters more)

        List<CloudletSimple> cloudlets = new ArrayList<>(tasks.size());
        Map<Long, Integer> bindCounts = new TreeMap<>();
        int offset = 0;
        int scheduled = 0;

        for (WorkloadLoader.WorkloadTask t : tasks) {
            CloudletSimple c = WorkloadLoader.createCloudletFrom(t);
            double arrival = t.getArrivalTimeSeconds();
//            try { c.setSubmissionDelay(arrival / 50); } catch (NoSuchMethodError ignore) {}

            double lengthMi = lengthMiLike(t);

            // predictor output (optional)
            double predTh = 0.0;
            try { if (predictor != null) predTh = predictor.getPredictedThroughput(arrival); }
            catch (Throwable ignored) {}

            Vm bestVm = null;
            double bestObj = Double.POSITIVE_INFINITY;
            double bestCompletion = Double.POSITIVE_INFINITY;
            double bestCost = Double.POSITIVE_INFINITY;

            for (int k = 0; k < vms.size(); k++) {
                Vm vm = vms.get((k + offset) % vms.size());

                double baseCapacity = capacityMiPerSec(vm);

                // Adjust capacity using predictor (treat predTh as "system is faster" signal)
                // Keep bounded to avoid crazy values.
                double capBoost = 1.0 + predictorWeight * Math.max(-0.5, Math.min(2.0, predTh / 10.0));
                double capacity = Math.max(1.0, baseCapacity * capBoost);

                // decay backlog to arrival
                double prevT = lastUpd.getOrDefault(vm, 0.0);
                double backlog = backlogMi.getOrDefault(vm, 0.0);
                double dt = Math.max(0.0, arrival - prevT);
                backlog = Math.max(0.0, backlog - capacity * dt);

                double newBacklog = backlog + lengthMi;
                double completion = arrival + (newBacklog / capacity);

                // expected $ cost (approx)
                double costSec = costPerSecond(vm);
                double execSeconds = lengthMi / capacity;
                double expectedCost = costSec * execSeconds;

                // objective in dollars
                double objective = expectedCost + dollarsPerSecondLatency * completion;

                if (objective < bestObj - EPS
                        || (Math.abs(objective - bestObj) <= EPS && expectedCost < bestCost - EPS)
                        || (Math.abs(objective - bestObj) <= EPS && Math.abs(expectedCost - bestCost) <= EPS
                        && completion < bestCompletion - EPS)) {
                    bestObj = objective;
                    bestVm = vm;
                    bestCost = expectedCost;
                    bestCompletion = completion;
                }
            }

            if (bestVm == null) bestVm = vms.get(0);

            broker.bindCloudletToVm(c, bestVm);
            c.setVm(bestVm);

            cloudlets.add(c);
            bindCounts.merge(bestVm.getId(), 1, Integer::sum);

            // update backlog for chosen vm using base capacity (or same boosted capacity; choose one consistently)
            double capacity = capacityMiPerSec(bestVm);
            double prevT = lastUpd.getOrDefault(bestVm, 0.0);
            double backlog = backlogMi.getOrDefault(bestVm, 0.0);
            double dt = Math.max(0.0, arrival - prevT);
            backlog = Math.max(0.0, backlog - capacity * dt);
            backlogMi.put(bestVm, backlog + lengthMi);
            lastUpd.put(bestVm, arrival);

            scheduled++;
            if (scheduled % ROTATE_EVERY_TASKS == 0) offset = (offset + 1) % vms.size();
        }

        System.out.println("Final Bindings " + bindCounts);
        broker.submitCloudletList(cloudlets);
        return cloudlets;
    }
}