package org.example;

import weka.classifiers.trees.RandomForest;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.CSVLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.example.Main.*;

public class WekaDetector {
    private static final Logger LOGGER = Logger.getLogger(WekaDetector.class.getName());

    // Time windows for analysis (in simulation time units)
    private static final double SHORT_WINDOW = 1.0;
    private static final double MEDIUM_WINDOW = 10.0;

    // Store recent requests for analysis
    private final List<RequestDetails> recentRequests = new ArrayList<>();

    // Request count per source (sourceId -> count)
    private final Map<Integer, Integer> sourceRequestCounts = new HashMap<>();

    // Last request timestamp per source
    private final Map<Integer, Double> lastRequestTimes = new HashMap<>();

    private final RandomForest model;
    private final Instances dataHeader;

    public WekaDetector() {
        try {
            LOGGER.info("Initializing WekaDetector...");

            // Load model
            File modelFile = new File("ddos_model.model");
            if (!modelFile.exists()) {
                LOGGER.severe("Model file not found: " + modelFile.getAbsolutePath());
                throw new RuntimeException("Model file not found");
            }

            model = (RandomForest) SerializationHelper.read(modelFile.getPath());
            LOGGER.info("Loaded Random Forest model");

            // Create empty dataset with same structure for prediction
            CSVLoader loader = new CSVLoader();
            loader.setSource(new File("ddos_training_data.csv"));
            dataHeader = loader.getDataSet();
            dataHeader.setClassIndex(dataHeader.numAttributes() - 1);

            LOGGER.info("Initialized with " + dataHeader.numAttributes() + " attributes");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize detector", e);
            throw new RuntimeException("Failed to initialize detector", e);
        }
    }

    public boolean isAttack(RequestDetails request, double currentTime) {
        try {
            // Update analytics with new request
            updateAnalytics(request, currentTime);

            // Extract features in the same order as training data
            double sourceRate = calculateSourceRate(request.getSourceId(), currentTime);
            double systemRate = calculateSystemRate(currentTime);
            double payloadSize = request.getPayloadSize();
            double cpuDemand = request.getCloudlet().getUtilizationOfCpu(0);
            double bwDemand = request.getCloudlet().getUtilizationOfBw(0);

            // Create instance with the correct number of attributes
            DenseInstance instance = new DenseInstance(dataHeader.numAttributes());
            instance.setDataset(dataHeader);

            // Set attribute values in the same order as training data
            instance.setValue(0, sourceRate);
            instance.setValue(1, systemRate);
            instance.setValue(2, payloadSize);
            instance.setValue(3, cpuDemand);
            instance.setValue(4, bwDemand);

            // Predict
            double[] distribution = model.distributionForInstance(instance);
            boolean isAttack = distribution[1] > 0.5;

            if (isAttack) {
                LOGGER.info(String.format("Detected potential attack - Source: %d, Rate: %.2f, System Rate: %.2f",
                        request.getSourceId(), sourceRate, systemRate));
            }

            return isAttack;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during attack detection", e);
            return false;
        }
    }

    private void updateAnalytics(RequestDetails request, double currentTime) {
        // Add to recent requests
        recentRequests.add(request);

        // Clean up old requests outside analysis window
        recentRequests.removeIf(r -> (currentTime - r.getTimestamp()) > MEDIUM_WINDOW);

        // Update source request count
        sourceRequestCounts.put(request.getSourceId(),
                sourceRequestCounts.getOrDefault(request.getSourceId(), 0) + 1);

        // Update last request time
        lastRequestTimes.put(request.getSourceId(), currentTime);
    }

    private double calculateSourceRate(int sourceId, double currentTime) {
        // Count requests from this source in the short window
        int requestCount = 0;
        double startTime = currentTime - SHORT_WINDOW;

        for (RequestDetails req : recentRequests) {
            if (req.getSourceId() == sourceId && req.getTimestamp() >= startTime) {
                requestCount++;
            }
        }

        return requestCount / SHORT_WINDOW;
    }

    private double calculateSystemRate(double currentTime) {
        // Count all requests in the medium window
        double startTime = currentTime - MEDIUM_WINDOW;
        int requestCount = 0;

        for (RequestDetails req : recentRequests) {
            if (req.getTimestamp() >= startTime) {
                requestCount++;
            }
        }

        return requestCount / MEDIUM_WINDOW;
    }
}