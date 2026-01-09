package com.cloudsim;

import com.cloudsim.allocater.CostAwareAllocater;
import com.cloudsim.scheduler.FIFOScheduler;
import com.cloudsim.scheduler.PriorityScheduler;
import com.cloudsim.workload.WorkloadLoader;
import com.cloudsim.workload.WorkloadLoader.WorkloadTask;


import com.google.gson.internal.LinkedTreeMap;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy;
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

public class Main {
    public static void main(String[] args) throws Exception {
        String workloadCsv = "src/main/java/com/cloudsim/workload/cloud_workload_dataset.csv";
        // 1️⃣ Load workload dataset
        InputStream in = new FileInputStream(workloadCsv);
        List<WorkloadTask> tasks = WorkloadLoader.load(in);

        // 2️⃣ Initialize CloudSim
        CloudSim sim = new CloudSim();
        // 3️⃣ Create Datacenter using custom CostAwareAllocator
        DatacenterSimple datacenter = createDatacenter(sim);

        // 4️⃣ Create Broker
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(sim);

        // 5️⃣ Create VMs (no chaining)
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            VmSimple vm = new VmSimple(10000, 4); // MIPS, PEs
            vm.setId(i);
            vm.setRam(8192);      // 8 GB
            vm.setBw(2000);       // 2 Gbps
            vm.setSize(10000);
//            vm.setTaskTime(tasks.get(i).startTime);

            vm.setCloudletScheduler(new CloudletSchedulerTimeShared());
            vms.add(vm);
        }
        broker.submitVmList(vms);

        // 6️⃣ Create Cloudlets based on workload
//        List<Cloudlet> cloudlets = new ArrayList<>();
//        for (int i = 0; i < tasks.size(); i++) {
//            WorkloadTask t = tasks.get(i);
//            CloudletSimple cloudlet = WorkloadLoader.createCloudletFrom(t);
//            Vm assignedVm = vms.get(i % vms.size()); // round-robin VM assignment
//            cloudlet.setVm(assignedVm);
//            cloudlets.add(cloudlet);
//        }
//        broker.submitCloudletList(cloudlets);
//        List<CloudletSimple> cloudlets = FIFOScheduler.schedule(tasks, vms, broker);
        List<CloudletSimple> cloudlets = PriorityScheduler.schedule(tasks, vms, broker);


        // 7️⃣ Start Simulation
        sim.start();

        // 8️⃣ Print Results
        List<Cloudlet> finished = broker.getCloudletFinishedList();
        System.out.println("\n✅ Simulation completed successfully.");
        System.out.println("Completed Cloudlets: " + finished.size());
        System.out.println("\n=== Execution Summary ===");
        for (Cloudlet c : finished) {
            System.out.printf("Cloudlet %3d | VM %d | Start: %.2f | Finish: %.2f | Length: %d%n",
                    c.getId(), c.getVm().getId(), c.getExecStartTime(), c.getFinishTime(), (long) c.getLength());
        }

        // 🧹 Cleanup
        finished.clear();
        vms.clear();
        cloudlets.clear();
        System.gc();
        System.out.println("\n🧠 Cost-aware allocation completed & memory cleaned up.");
    }

    /** Creates a Datacenter with 3 heterogeneous Hosts using our custom allocator */
    private static DatacenterSimple createDatacenter(CloudSim sim) {
        List<Host> hostList = new ArrayList<>();

        for (int h = 0; h < 3; h++) {
            List<Pe> peList = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                peList.add(new PeSimple(10000));
            }


            int ram = 131072 + h * 65536;     // 128GB, 192GB, 256GB
            long bw = 2_000_000 + h * 1_000_000; // variable bandwidth
            long storage = 1_000_000_000;     // 1 TB

            HostSimple host = new HostSimple(ram, bw, storage, peList);
            host.setVmScheduler(new VmSchedulerTimeShared());
//            host.getCharacteristics().setCostPerSecond(0.8 + 0.2 * h);
            hostList.add(host);
        }
        String lstmCsv = "C:\\Users\\HP\\OneDrive\\Scans\\Desktop\\New folder\\cloud-cost-scheduler\\results\\cloud_workload_with_predictions.csv";
// 🔗 Pass predictor into the allocator
        WorkloadPredictor predictor = new WorkloadPredictor(lstmCsv);
        CostAwareAllocater policy = new CostAwareAllocater(predictor);
        return new DatacenterSimple(sim, hostList, policy);
    }
    public void registerVmTimestamp(int vmId, LocalDateTime time) {
        final Map<Integer, LocalDateTime> vmTimestamp = new HashMap<>();
        vmTimestamp.put(vmId,time);
    }
}

