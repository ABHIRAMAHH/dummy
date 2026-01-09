package com.cloudsim.ml;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.Reader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads LSTM-predicted workload CSV and provides predicted throughput
 * based on timestamp.
 */
public class WorkloadPredictor {

    private final Map<LocalDateTime, Double> throughputMap = new HashMap<>();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public WorkloadPredictor(String csvFilePath) {
        loadPredictions(csvFilePath);
    }

    /** Loads predicted throughput (tasks/sec) from the CSV file */
    private void loadPredictions(String csvFilePath) {
        try (Reader in = new FileReader(csvFilePath)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .parse(in);

            for (CSVRecord record : records) {
                LocalDateTime timestamp = LocalDateTime.parse(record.get("Task_Start_Time"), formatter);
                double predictedThroughput = Double.parseDouble(record.get("Predicted_Throughput (tasks/sec)"));
                throughputMap.put(timestamp, predictedThroughput);
            }

            System.out.println("✅ Loaded " + throughputMap.size() + " LSTM predictions from CSV.");
        } catch (Exception e) {
            System.err.println("⚠ Error loading LSTM predictions: " + e.getMessage());
        }
    }

    /** Returns the nearest predicted throughput for the given timestamp */
    public double getPredictedThroughput(LocalDateTime timestamp) {
        return throughputMap.getOrDefault(timestamp, 5.0); // Default avg throughput
    }
}