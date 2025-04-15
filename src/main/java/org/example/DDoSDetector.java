package org.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.example.Main.*;

public class DDoSDetector {
    // Time windows for analysis (in simulation time units)
    private static final double SHORT_WINDOW = 1.0;
    private static final double MEDIUM_WINDOW = 10.0;
    private static final double LONG_WINDOW = 60.0;

    // Store recent requests for analysis
    private final List<RequestDetails> recentRequests = new ArrayList<>();

    // Request count per source (sourceId -> count)
    private final Map<Integer, Integer> sourceRequestCounts = new HashMap<>();

    // Last request timestamp per source
    private final Map<Integer, Double> lastRequestTimes = new HashMap<>();

    // ML model (would be trained offline)
    private final RandomForestClassifier model;

    public DDoSDetector() {
        // Load pre-trained model
        this.model = loadModel();
    }

    public boolean isAttack(RequestDetails request, double currentTime) {
        // Update analytics with new request
        updateAnalytics(request, currentTime);

        // Extract features
        double[] features = extractFeatures(request, currentTime);

        // Predict using model
        return model.predict(features);
    }

    private void updateAnalytics(RequestDetails request, double currentTime) {
        // Add to recent requests
        recentRequests.add(request);

        // Clean up old requests outside analysis window
        recentRequests.removeIf(r -> (currentTime - r.getTimestamp()) > LONG_WINDOW);

        // Update source request count
        sourceRequestCounts.put(request.getSourceId(),
                sourceRequestCounts.getOrDefault(request.getSourceId(), 0) + 1);

        // Update last request time
        lastRequestTimes.put(request.getSourceId(), currentTime);
    }

    private double[] extractFeatures(RequestDetails request, double currentTime) {
        int sourceId = request.getSourceId();

        // Feature 1: Request rate from this source (short window)
        double sourceRateShort = countRequestsFromSource(sourceId, currentTime - SHORT_WINDOW, currentTime);

        // Feature 2: Request rate from this source (medium window)
        double sourceRateMedium = countRequestsFromSource(sourceId, currentTime - MEDIUM_WINDOW, currentTime);

        // Feature 3: System-wide request rate
        double systemRate = recentRequests.size() / MEDIUM_WINDOW;

        // Feature 4: Time since last request from this source
        double timeSinceLastRequest = lastRequestTimes.containsKey(sourceId) ?
                currentTime - lastRequestTimes.get(sourceId) : Double.MAX_VALUE;

        // Feature 5: Resource demand (normalized)
        double cpuDemand = request.getCloudlet().getUtilizationOfCpu(0);
        double ramDemand = request.getCloudlet().getUtilizationOfRam(0);
        double bwDemand = request.getCloudlet().getUtilizationOfBw(0);

        // Feature 6: Request size metrics (would be available in real systems)
        double payloadSize = request.getPayloadSize();

        // More features...

        return new double[] {
                sourceRateShort, sourceRateMedium, systemRate, timeSinceLastRequest,
                cpuDemand, ramDemand, bwDemand, payloadSize
                // Additional features...
        };
    }

    private int countRequestsFromSource(int sourceId, double startTime, double endTime) {
        return (int) recentRequests.stream()
                .filter(r -> r.getSourceId() == sourceId)
                .filter(r -> r.getTimestamp() >= startTime && r.getTimestamp() <= endTime)
                .count();
    }

    private RandomForestClassifier loadModel() {
        // In a real system, you would load a pre-trained model from disk
        // For simulation, we can create a simple model
        return new RandomForestClassifier();
    }

    // Simple placeholder for ML model
    private static class RandomForestClassifier {
        public boolean predict(double[] features) {
            // For simulation purposes, implement a simple heuristic
            // In reality, this would be a trained ML model

            // Example simple rule: if source sends more than 10 requests per second
            if (features[0] > 10) return true;

            // More sophisticated logic would be used in a real model
            return false;
        }
    }
}