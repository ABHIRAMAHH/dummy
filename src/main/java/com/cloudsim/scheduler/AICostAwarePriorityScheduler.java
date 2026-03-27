package com.cloudsim.scheduler;

import com.cloudsim.WorkloadPredictor;
import com.cloudsim.output.ResultsExporter;
import com.cloudsim.workload.CloudletMetadata;
import com.cloudsim.workload.WorkloadLoader;
import com.cloudsim.workload.WorkloadLoader.WorkloadTask;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;

import static com.cloudsim.scheduler.SchedulerUtil.*;

/**
 * ============================================================
 *  ASB-Dynamic Scheduler v4 (Adaptive Score-Based Dynamic)
 *
 *  ROOT CAUSE FIX:
 *  VM4 (3000 MIPS) always won on EFT because it processes
 *  tasks 3x faster, so its EFT is always lower UNTIL it has
 *  3x more backlog than slow VMs. This causes 47% task pile-up.
 *
 *  SOLUTION: Cost-Penalized EFT (CPEFT)
 *
 *  CPEFT(i,j) = EFT(i,j) × (1 + costNorm[j] × costPenaltyFactor)
 *
 *  This makes expensive VMs appear "slower" in the score,
 *  forcing the scheduler to balance cost vs speed naturally.
 *
 *  Additionally: soft task-count cap discourages piling onto
 *  any single VM beyond its fair share.
 *
 *  FULL FORMULA:
 *  Score(i,j) = w[0] * CPEFTnorm(i,j)           [cost-penalized EFT]
 *             + w[1] * PriorityMismatch(i,j)     [priority-speed match]
 *             - w[2] * AIBoost(predTh,j)         [CNN-GRU reward]
 *             - w[3] * AffinityBonus(i,j)        [diversity]
 *             + w[4] * OverloadPenalty(j)        [soft cap]
 *
 *  Weights adapt every ADAPT_INTERVAL via EMA on prediction error.
 * ============================================================
 */
public class AICostAwarePriorityScheduler {

    // ── Constants ─────────────────────────────────────────────
    private static final int    ADAPT_INTERVAL    = 20;
    private static final double ALPHA             = 0.25;
    private static final int    PRESSURE_WINDOW   = 30;

    // KEY TUNING: how much to penalize expensive VMs in EFT
    // 2.0 = VM with costNorm=1.0 appears 3x slower in score
    // Increase this if VM4 still dominates
    private static final double COST_PENALTY_FACTOR = 2.0;

    // Soft cap: penalize VMs that exceed their fair share by this ratio
    // 1.5 = start penalizing when VM has 50% more than average tasks
    private static final double OVERLOAD_RATIO = 1.5;
    // ─────────────────────────────────────────────────────────

    public static List<CloudletSimple> schedule(
            List<WorkloadTask>     tasks,
            List<Vm>               vms,
            DatacenterBrokerSimple broker,
            WorkloadPredictor      predictor
    ) {
        int V = vms.size();
        int T = tasks.size();
        double fairShare = (double) T / V; // expected tasks per VM

        // ── 1. VM static properties ───────────────────────────
        double[] vmMips       = new double[V];
        double[] vmCostPerSec = new double[V];
        int[]    vmPes        = new int[V];
        double[] vmEffective  = new double[V]; // mips × pes

        for (int j = 0; j < V; j++) {
            Vm vm            = vms.get(j);
            vmMips[j]        = vm.getMips();
            vmPes[j]         = (int) vm.getNumberOfPes();
            vmCostPerSec[j]  = costPerSecond(vm);
            vmEffective[j]   = vmMips[j] * vmPes[j];
        }

        // Normalize costs → [0,1]
        double maxCost   = Arrays.stream(vmCostPerSec).max().orElse(1.0);
        double minCost   = Arrays.stream(vmCostPerSec).min().orElse(0.0);
        double costRange = Math.max(maxCost - minCost, 1e-9);
        double[] vmCostNorm = new double[V];
        for (int j = 0; j < V; j++)
            vmCostNorm[j] = (vmCostPerSec[j] - minCost) / costRange;

        // Normalize speed → [0,1]  (1=fastest)
        double maxMips   = Arrays.stream(vmMips).max().orElse(1.0);
        double minMips   = Arrays.stream(vmMips).min().orElse(0.0);
        double mipsRange = Math.max(maxMips - minMips, 1e-9);
        double[] vmSpeedNorm = new double[V];
        for (int j = 0; j < V; j++)
            vmSpeedNorm[j] = (vmMips[j] - minMips) / mipsRange;

        // ── 2. Pre-scan throughput for global range ───────────
        double globalMinTh =  Double.MAX_VALUE;
        double globalMaxTh = -Double.MAX_VALUE;
        for (int i = 0; i < tasks.size(); i++) {
            double val = safePredict(predictor, tasks.get(i), i);
            if (val < globalMinTh) globalMinTh = val;
            if (val > globalMaxTh) globalMaxTh = val;
        }
        double globalThRange = Math.max(globalMaxTh - globalMinTh, 1e-9);
        System.out.printf(
                "📊 Throughput range: min=%.2f  max=%.2f  range=%.2f%n",
                globalMinTh, globalMaxTh, globalThRange);

        // ── 3. Sort: HIGH priority first, then SHORT tasks ────
        List<WorkloadTask> sorted = new ArrayList<>(tasks);
        sorted.sort(Comparator
                .comparingInt(AICostAwarePriorityScheduler::priorityRank)
                .thenComparingDouble(t -> t.taskExecutionTime));

        // ── 4. Weights ────────────────────────────────────────
        // w[0]=CPEFT  w[1]=priority-mismatch
        // w[2]=AI-boost(-)  w[3]=affinity(-)  w[4]=overload(+)
        double[] w = { 0.40, 0.20, 0.15, 0.10, 0.15 };

        // ── 5. State trackers ─────────────────────────────────
        double[] backlog      = new double[V];  // queued seconds
        int[]    taskCount    = new int[V];     // tasks assigned so far
        int[][]  priorityCount= new int[V][3]; // [vm][High/Med/Low]

        // ── 6. Rolling window for pressure ───────────────────
        Deque<Double> predWindow = new ArrayDeque<>(PRESSURE_WINDOW);

        // ── 7. Weight adaptation state ────────────────────────
        double rollingPredError = 0.0;
        double lastPredTh       = safePredict(predictor, null, 0);
        int    tasksSinceAdapt  = 0;

        // ── 8. Result containers ──────────────────────────────
        List<CloudletSimple> cloudlets  = new ArrayList<>(sorted.size());
        Map<Long, Integer>   bindCounts = new TreeMap<>();

        // ── 9. Main scheduling loop ───────────────────────────
        for (int i = 0; i < sorted.size(); i++) {
            WorkloadTask task = sorted.get(i);

            // Task length in MI
            long lengthMI = Math.max(100,
                    (long)((task.taskExecutionTime * task.cpuUtilization) / 50.0));

            // CNN-GRU prediction
            double predTh = safePredict(predictor, task, i);
            predTh = Math.max(1.0, Math.min(25.0, predTh));

            // Update rolling window
            predWindow.addLast(predTh);
            if (predWindow.size() > PRESSURE_WINDOW)
                predWindow.pollFirst();

            // Relative throughput pressure [0,1]
            double relativePressure = (predTh - globalMinTh) / globalThRange;
            double wMin   = predWindow.stream().mapToDouble(Double::doubleValue).min().orElse(predTh);
            double wMax   = predWindow.stream().mapToDouble(Double::doubleValue).max().orElse(predTh);
            double wRange = Math.max(wMax - wMin, 1e-9);
            double windowPressure    = (predTh - wMin) / wRange;
            double throughputPressure = 0.6 * relativePressure + 0.4 * windowPressure;

            // Task priority
            int    pRank            = priorityRank(task);
            double priorityPressure = (2 - pRank) / 2.0;

            // ── Compute CPEFT for each VM ─────────────────────
            // CPEFT = EFT × (1 + costNorm × COST_PENALTY_FACTOR)
            // This makes VM4 (costNorm=1.0) appear 3x slower in scoring
            // while VM0 (costNorm=0.0) keeps its raw EFT advantage
            double[] cpeft    = new double[V];
            double   maxCPEFT = 0;
            double   minCPEFT = Double.MAX_VALUE;

            for (int j = 0; j < V; j++) {
                double rawEFT = backlog[j] + (double) lengthMI / vmEffective[j];
                double costPenalty = 1.0 + vmCostNorm[j] * COST_PENALTY_FACTOR;
                cpeft[j] = rawEFT * costPenalty;
                if (cpeft[j] > maxCPEFT) maxCPEFT = cpeft[j];
                if (cpeft[j] < minCPEFT) minCPEFT = cpeft[j];
            }
            double cpeftRange = Math.max(maxCPEFT - minCPEFT, 1e-9);

            // ── Score each VM ─────────────────────────────────
            double bestScore = Double.MAX_VALUE;
            int    bestVm    = 0;

            for (int j = 0; j < V; j++) {

                // C1: Cost-Penalized EFT normalized [0,1]
                // Combines speed + cost into single metric
                // Cheap+slow VM competes fairly with expensive+fast VM
                double cpeftScore = (cpeft[j] - minCPEFT) / cpeftRange;

                // C2: Priority-speed mismatch
                // High priority task on slow VM = bad
                // Scales with throughput pressure (busy → matters more)
                double speedPenalty     = 1.0 - vmSpeedNorm[j];
                double priorityMismatch = priorityPressure * speedPenalty
                        * (0.5 + 0.5 * throughputPressure);

                // C3: AI Boost (SUBTRACTED = reward)
                // CNN-GRU says system is busy + VM has low backlog → reward
                double maxBL = Arrays.stream(backlog).max().orElse(1.0);
                double normalizedBacklog = backlog[j] / Math.max(maxBL, 1e-9);
                double aiBoost = throughputPressure * (1.0 - normalizedBacklog);

                // C4: Affinity bonus (SUBTRACTED = reward diversity)
                double affinityBonus = computeAffinityBonus(priorityCount[j], pRank);

                // C5: Overload penalty (ADDED = discourage piling)
                // Kicks in when VM exceeds OVERLOAD_RATIO × fairShare tasks
                double overloadPenalty = 0.0;
                if (taskCount[j] > OVERLOAD_RATIO * fairShare) {
                    // Grows linearly with excess tasks
                    overloadPenalty = (taskCount[j] - OVERLOAD_RATIO * fairShare)
                            / Math.max(fairShare, 1.0);
                    // Cap at 1.0 so it doesn't completely dominate
                    overloadPenalty = Math.min(1.0, overloadPenalty);
                }

                // ── Composite score: LOWER = better ──────────
                double score = w[0] * cpeftScore
                        + w[1] * priorityMismatch
                        - w[2] * aiBoost
                        - w[3] * affinityBonus
                        + w[4] * overloadPenalty;

                if (score < bestScore) {
                    bestScore = score;
                    bestVm    = j;
                }
            }

            // ── Assign task to best VM ────────────────────────
            Vm selectedVm = vms.get(bestVm);

            CloudletSimple cloudlet = WorkloadLoader.createCloudletFrom(task);
            cloudlet.setSubmissionDelay(task.arrivalSeconds / 50.0);
            cloudlet.setVm(selectedVm);
            broker.bindCloudletToVm(cloudlet, selectedVm);

            cloudlets.add(cloudlet);
            bindCounts.merge(selectedVm.getId(), 1, Integer::sum);

            // Update trackers
            backlog[bestVm]   += (double) lengthMI / vmEffective[bestVm];
            taskCount[bestVm] ++;
            priorityCount[bestVm][pRank]++;

            // ── Adaptive weight update ────────────────────────
            tasksSinceAdapt++;
            double currentPredTh = safePredict(predictor, task, i);
            rollingPredError = ALPHA * Math.abs(currentPredTh - lastPredTh)
                    + (1.0 - ALPHA) * rollingPredError;
            lastPredTh = currentPredTh;

            if (tasksSinceAdapt >= ADAPT_INTERVAL) {
                adaptWeights(w, rollingPredError, throughputPressure);
                tasksSinceAdapt = 0;
            }
        }

        // ── 10. Submit ────────────────────────────────────────
        broker.submitCloudletList(cloudlets);

        // ── 11. Summary ───────────────────────────────────────
        System.out.println("Final Bindings: " + bindCounts);
        printSummary(cloudlets, vms, backlog, taskCount, w, fairShare);
        Map<String, Double> weightsMap = new LinkedHashMap<>();
        weightsMap.put("cpeft",     Math.round(w[0]*100.0)/100.0);
        weightsMap.put("priority",  Math.round(w[1]*100.0)/100.0);
        weightsMap.put("ai",        Math.round(w[2]*100.0)/100.0);
        weightsMap.put("affinity",  Math.round(w[3]*100.0)/100.0);
        weightsMap.put("overload",  Math.round(w[4]*100.0)/100.0);

        ResultsExporter.addResult(
                buildResult("AI_COST_PRIORITY", bindCounts, vms, backlog,
                        globalMinTh, globalMaxTh, weightsMap)
        );
        return cloudlets;
    }

    // ─────────────────────────────────────────────────────────
    // safePredict
    // ─────────────────────────────────────────────────────────
    private static double safePredict(WorkloadPredictor predictor,
                                      WorkloadTask task,
                                      double fallbackIndex) {
        try {
            if (task != null && task.startTime != null) {
                double val = predictor.getPredictedThroughput(task.startTime);
                if (val > 5.01) return val;
            }
            return predictor.getPredictedThroughput(fallbackIndex);
        } catch (Exception e) {
            return 8.0;
        }
    }

    // ─────────────────────────────────────────────────────────
    // priorityRank: 0=High, 1=Medium, 2=Low
    // ─────────────────────────────────────────────────────────
    private static int priorityRank(WorkloadTask t) {
        if (t.jobPriority == null) return 2;
        switch (t.jobPriority.trim().toLowerCase()) {
            case "high":   return 0;
            case "medium": return 1;
            default:       return 2;
        }
    }

    // ─────────────────────────────────────────────────────────
    // affinityBonus [0..1]
    // ─────────────────────────────────────────────────────────
    private static double computeAffinityBonus(int[] counts, int pRank) {
        int total = counts[0] + counts[1] + counts[2];
        if (total == 0) return 1.0;
        return 1.0 - ((double) counts[pRank] / total);
    }

    // ─────────────────────────────────────────────────────────
    // adaptWeights
    // busy  → w[0](CPEFT)↑, w[2](AI)↑, w[4](overload)↑
    // idle  → w[0](CPEFT)↓, w[2](AI)↓, cost implicitly favored
    // ─────────────────────────────────────────────────────────
    private static void adaptWeights(double[] w,
                                     double predError,
                                     double throughputPressure) {
        double errorSignal = Math.min(predError / 10.0, 1.0);
        double shift = ALPHA * errorSignal * 0.06;

        if (throughputPressure > 0.5) {
            // Busy: tighten load balance, boost AI signal
            w[0] = Math.min(0.55, w[0] + shift); // CPEFT up
            w[2] = Math.min(0.30, w[2] + shift); // AI up
            w[4] = Math.min(0.25, w[4] + shift); // overload up
        } else {
            // Idle: relax load constraints
            w[0] = Math.max(0.25, w[0] - shift); // CPEFT down
            w[2] = Math.max(0.05, w[2] - shift); // AI down
            w[4] = Math.max(0.05, w[4] - shift); // overload down
        }

        // Re-normalize to sum=1
        double sum = 0;
        for (double wi : w) sum += Math.abs(wi);
        if (sum > 1e-9)
            for (int k = 0; k < w.length; k++) w[k] = Math.abs(w[k]) / sum;
    }

    // ─────────────────────────────────────────────────────────
    // printSummary
    // ─────────────────────────────────────────────────────────
    private static void printSummary(List<CloudletSimple> cloudlets,
                                     List<Vm> vms,
                                     double[] backlog,
                                     int[] taskCount,
                                     double[] finalWeights,
                                     double fairShare
                                     ) {
        int V = vms.size();

        double mean = Arrays.stream(taskCount).average().orElse(0);
        double var  = 0;
        for (int c : taskCount) var += Math.pow(c - mean, 2);
        double stdDev = Math.sqrt(var / V);

        System.out.println(
                "\n╔══════════════════════════════════════════════════════════╗");
        System.out.println(
                "║    ASB-Dynamic Scheduler v4 — Assignment Summary         ║");
        System.out.println(
                "╠══════════════════════════════════════════════════════════╣");
        System.out.printf("║  Fair share per VM: %-5.0f                               ║%n",
                fairShare);
        System.out.println(
                "╠══════════════════════════════════════════════════════════╣");
        System.out.printf("║  %-6s %-8s %-8s %-10s %-12s ║%n",
                "VM_ID","Tasks","MIPS","Cost/Sec","Backlog(s)");
        System.out.println(
                "╠══════════════════════════════════════════════════════════╣");

        for (int j = 0; j < V; j++) {
            Vm vm = vms.get(j);
            String flag = taskCount[j] > OVERLOAD_RATIO * fairShare ? " ⚠" : "  ";
            System.out.printf("║  %-6d %-8d %-8.0f %-10.2f %-10.2f%s ║%n",
                    vm.getId(), taskCount[j], vm.getMips(),
                    costPerSecond(vm), backlog[j], flag);
        }

        System.out.println(
                "╠══════════════════════════════════════════════════════════╣");
        System.out.printf(
                "║  Load StdDev: %-8.2f (target < %.0f)                  ║%n",
                stdDev, fairShare * 0.3);
        System.out.printf(
                "║  Weights: CPEFT=%.2f pri=%.2f AI=%.2f aff=%.2f ovl=%.2f  ║%n",
                finalWeights[0], finalWeights[1], finalWeights[2],
                finalWeights[3], finalWeights[4]);
        System.out.println(
                "╚══════════════════════════════════════════════════════════╝\n");
        // build weights map

    }
}