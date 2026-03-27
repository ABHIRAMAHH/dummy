package com.cloudsim;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.Reader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Reads LSTM-predicted workload CSV and provides predicted throughput
 * based on timestamp.
 */

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.Reader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.Map;

public class WorkloadPredictor {

    private final Map<LocalDateTime, Double> throughputMap = new HashMap<>();

    // Accept "yyyy-MM-dd HH:mm:ss" and "yyyy-MM-dd HH:mm"
    private final DateTimeFormatter predFormatter = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm")
            .optionalStart()
            .appendPattern(":ss")
            .optionalEnd()
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .toFormatter();

    private LocalDateTime baseTime = null;

    public WorkloadPredictor(String csvFilePath) {
        loadPredictions(csvFilePath);
    }

    public LocalDateTime getBaseTime() {
        return baseTime;
    }

    private void loadPredictions(String csvFilePath) {
        try (Reader in = new FileReader(csvFilePath)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .parse(in);

            int count = 0;
            for (CSVRecord record : records) {
                LocalDateTime ts = LocalDateTime.parse(record.get("Task_Start_Time"), predFormatter);
                double pred = Double.parseDouble(record.get("Predicted_Throughput (tasks/sec)"));
                throughputMap.put(ts, pred);
                if (baseTime == null) baseTime = ts;
                count++;
            }
            System.out.println("✅ Loaded " + count + " throughput predictions.");
            System.out.println("🕒 Predictor baseTime: " + baseTime);
        } catch (Exception e) {
            System.err.println("⚠ Error loading predictions: " + e.getMessage());
        }
    }

    /** Query by dataset timestamp */
//    public double getPredictedThroughput(LocalDateTime timestamp) {
//        return throughputMap.getOrDefault(timestamp, 5.0);
//    }
    /**
     * Get predicted throughput by task timestamp with fallback interpolation.
     * Fixes the SEQ_LEN warmup gap where first 13 rows return 5.0.
     */
    public double getPredictedThroughput(LocalDateTime timestamp) {
        Double val = throughputMap.get(timestamp);

        // If we got a real prediction (not the 5.0 warmup default), use it
        if (val != null && val > 5.01) return val;

        // Nearest-neighbor fallback for warmup rows
        return throughputMap.entrySet().stream()
                .filter(e -> e.getValue() > 5.01)
                .min(Comparator.comparingLong(e ->
                        Math.abs(java.time.Duration.between(e.getKey(), timestamp).toSeconds())
                ))
                .map(Map.Entry::getValue)
                .orElse(8.0); // mean of your dataset range [1..20]
    }
    /** Query by simulation clock (seconds since baseTime) */
    public double getPredictedThroughput(double simTimeSeconds) {
        if (baseTime == null) return 5.0;
        LocalDateTime ts = baseTime.plusSeconds((long)Math.floor(simTimeSeconds));
        return getPredictedThroughput(ts);
    }

    public void updateObservedThroughput(double observedThroughput) {
        // Optional: log/online learning hook
    }
}