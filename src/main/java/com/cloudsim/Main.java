package com.cloudsim;

import com.cloudsim.allocater.CostAwareAllocater;
import com.cloudsim.config.ConfigLoader;
import com.cloudsim.model.SimulationResult;
import com.cloudsim.scheduler.*;
import com.cloudsim.workload.WorkloadLoader;
import com.cloudsim.workload.WorkloadLoader.WorkloadTask;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



import com.cloudsim.allocater.CostAwareAllocater;
import com.cloudsim.config.ConfigLoader;
import com.cloudsim.scheduler.PriorityScheduler;
import com.cloudsim.workload.WorkloadLoader;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {
        final String workloadCsv = "src/main/java/com/cloudsim/workload/cloud_workload_dataset.csv";
        final String lstmCsv = "C:\\Users\\HP\\OneDrive\\Scans\\Desktop\\New folder\\cloud-cost-scheduler\\results\\cloud_workload_with_predictions_lstm.csv";
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off");
        // Put vm_types.json under src/main/resources/vm_types.json
        final String vmTypesPath = "src/main/java/com/cloudsim/config/vm_types.json";

        // 1) Load workload
        InputStream in = new FileInputStream(workloadCsv);
        List<WorkloadLoader.WorkloadTask> tasks = WorkloadLoader.load(in);

//        tasks = tasks.subList(0, 200);
        // 2) Predictor
        WorkloadPredictor predictor = new WorkloadPredictor(lstmCsv);


        Map<SchedulerType, Double> results = new LinkedHashMap<>();
        List<VmTemplate> templates = ConfigLoader.loadVmTemplates(vmTypesPath);
        if (templates == null || templates.isEmpty()) {
            throw new IllegalStateException("No VM templates loaded");
        }
        for (SchedulerType type : SchedulerType.values()) {

            double makespan = runSimulation(type, new ArrayList<>(tasks), templates, predictor);
            results.put(type, makespan);

            System.out.println(type + " Makespan = " + makespan);
        }

// 7) Comparison Table
        System.out.println("\n===== Scheduler Comparison =====");

        double baseline = results.get(SchedulerType.FIFO);

        for (var e : results.entrySet()) {
            double saved = baseline - e.getValue();

            System.out.printf("%-20s %-15.2f %-15.2f\n",
                    e.getKey(), e.getValue(), saved);
        }
    }

    private static DatacenterSimple createDatacenter(CloudSim sim, WorkloadPredictor predictor) {
        List<Host> hostList = new ArrayList<>();

        for (int h = 0; h < 3; h++) {
            List<Pe> peList = new ArrayList<>();
            for (int i = 0; i < 10; i++) peList.add(new PeSimple(10_000));

            int ram = 131_072 + h * 65_536;
            long bw = 10_000_000;  // fixed high bandwidth
            long storage = 1_000_000_000;

            HostSimple host = new HostSimple(ram, bw, storage, peList);
            host.setVmScheduler(new VmSchedulerTimeShared());
            hostList.add(host);
        }

        CostAwareAllocater policy = new CostAwareAllocater(predictor);
        return new DatacenterSimple(sim, hostList, policy);
    }
private static double runSimulation(
        SchedulerType type,
        List<WorkloadLoader.WorkloadTask> tasks,
        List<VmTemplate> templates,
        WorkloadPredictor predictor
) throws Exception {

    System.out.println("\n==============================");
    System.out.println("Running Scheduler: " + type);
    System.out.println("==============================");
    CloudSim sim = new CloudSim();
//    sim.terminateAt(100); // or 10000
    DatacenterSimple dc = createDatacenter(sim, predictor);
    DatacenterBrokerSimple broker = new DatacenterBrokerSimple(sim);

    // Create VMs again (IMPORTANT: fresh for each run)
    int[] pick = {0, 0, 1, 1, 2};
    List<Vm> vms = new ArrayList<>();
    int vmId = 0;

    for (int i = 0; i < pick.length; i++) {
        VmTemplate t = templates.get(pick[i]);

        CostVmSimple vm = new CostVmSimple(t.mips, t.pes);
        vm.setId(vmId++);
        vm.setRam(t.ram);
        vm.setBw(1_000_000);  // increase bandwidth
        vm.setSize(10_000);
        vm.setCloudletScheduler(new CloudletSchedulerTimeShared());
        vm.setCostPerSecond(t.costPerSec);

        vms.add(vm);
    }

    broker.submitVmList(vms);

    List<CloudletSimple> cloudlets;

    switch(type) {
        case PRIORITY:
            cloudlets = PriorityScheduler.schedule(tasks, vms, broker, predictor);
            break;

        case AI_COST_PRIORITY:
            cloudlets = AICostAwarePriorityScheduler.schedule(tasks, vms, broker, predictor);
            break;

        case ROUND_ROBIN:
            cloudlets = RoundRobinScheduler.schedule(tasks, vms, broker);
            break;

        case FIFO:
            cloudlets = FIFOScheduler.schedule(tasks, vms, broker);
            break;

        case SJF:
            cloudlets = SJFScheduler.schedule(tasks, vms, broker);
            break;

        case MINMIN:
            cloudlets = MinMinScheduler.schedule(tasks, vms, broker);
            break;

        case COST_GREEDY:
            cloudlets = CostOnlyGreedyScheduler.schedule(tasks, vms, broker);
            break;

        case PRIORITY_SJF:
            cloudlets = PrioritySJFScheduler.schedule(tasks, vms, broker, predictor);
            break;

        default:
            throw new IllegalStateException("Unknown scheduler");
    }

    System.out.println("Starting simulation for: " + type);

    sim.start();
    System.out.println("Completed simulation for: " + type);
    List<Cloudlet> finished = broker.getCloudletFinishedList();

    return finished.stream()
            .mapToDouble(Cloudlet::getFinishTime)
            .max()
            .orElse(0);
}
    public static SimulationResult runSimulationAPI(
            SchedulerType type
    ) throws Exception {

        final String workloadCsv = "src/main/java/com/cloudsim/workload/cloud_workload_dataset.csv";
        final String lstmCsv = "C:\\Users\\HP\\...\\cloud_workload_with_predictions_lstm.csv";
        final String vmTypesPath = "src/main/java/com/cloudsim/config/vm_types.json";

        InputStream in = new FileInputStream(workloadCsv);
        List<WorkloadLoader.WorkloadTask> tasks = WorkloadLoader.load(in);

        WorkloadPredictor predictor = new WorkloadPredictor(lstmCsv);
        List<VmTemplate> templates = ConfigLoader.loadVmTemplates(vmTypesPath);

        CloudSim sim = new CloudSim();
        sim.terminateAt(5000);

        DatacenterSimple dc = createDatacenter(sim, predictor);
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(sim);

        // Create VMs
        int[] pick = {0, 0, 1, 1, 2};
        List<Vm> vms = new ArrayList<>();
        int vmId = 0;

        for (int i = 0; i < pick.length; i++) {
            VmTemplate t = templates.get(pick[i]);

            CostVmSimple vm = new CostVmSimple(t.mips, t.pes);
            vm.setId(vmId++);
            vm.setRam(t.ram);
            vm.setBw(1_000_000);
            vm.setSize(10_000);
            vm.setCloudletScheduler(new CloudletSchedulerTimeShared());
            vm.setCostPerSecond(t.costPerSec);

            vms.add(vm);
        }

        broker.submitVmList(vms);

        List<CloudletSimple> cloudlets;

        switch(type) {
            case PRIORITY:
                cloudlets = PriorityScheduler.schedule(tasks, vms, broker, predictor);
                break;
            case ROUND_ROBIN:
                cloudlets = RoundRobinScheduler.schedule(tasks, vms, broker);
                break;
            case FIFO:
                cloudlets = FIFOScheduler.schedule(tasks, vms, broker);
                break;
            default:
                throw new IllegalStateException("Unknown scheduler");
        }

        sim.start();

        List<Cloudlet> finished = broker.getCloudletFinishedList();

        double makespan = finished.stream()
                .mapToDouble(Cloudlet::getFinishTime)
                .max()
                .orElse(0);

        // Example metrics (you can improve later)
        Map<Long, Integer> vmBindings = new HashMap<>();
        for (Cloudlet c : finished) {
            vmBindings.merge(c.getVm().getId(), 1, Integer::sum);
        }

        double totalCost = finished.size() * 0.01;
        double avgRamUtil = 0.5;

        return new SimulationResult(vmBindings, totalCost, makespan, avgRamUtil);
    }
}
