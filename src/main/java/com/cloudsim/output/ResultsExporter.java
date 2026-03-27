package com.cloudsim.output;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class ResultsExporter {

    private static final SimulationResult result = new SimulationResult();

    static {
        result.simulationRun = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        result.totalTasks = 1000;
        result.schedulers = new ArrayList<>();
    }

    public static void addResult(SimulationResult.SchedulerResult sr) {
        result.schedulers.add(sr);
    }

    // Called AFTER sim.start() to add post-simulation metrics
    public static void enrichLastResult(
            double makespan,
            double avgWaitTime,
            double totalCost,
            double totalExecTime
    ) {
        if (result.schedulers.isEmpty()) return;
        SimulationResult.SchedulerResult last =
                result.schedulers.get(result.schedulers.size() - 1);

        last.makespan      = Math.round(makespan * 100.0) / 100.0;
        last.avgWaitTime   = Math.round(avgWaitTime * 100.0) / 100.0;
        last.totalCost     = Math.round(totalCost * 100.0) / 100.0;
        last.totalExecTime = Math.round(totalExecTime * 100.0) / 100.0;
    }

    public static void export() {
        try {
            String outputPath = "ui/public/results.json";
            Files.createDirectories(Paths.get(outputPath).getParent());

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(outputPath)) {
                gson.toJson(result, writer);
            }
            System.out.println("\n✅ All results exported → " + outputPath);
        } catch (IOException e) {
            System.err.println("❌ Export failed: " + e.getMessage());
        }
    }
}