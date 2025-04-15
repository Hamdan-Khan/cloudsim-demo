package org.example;

import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.core.SerializationHelper;
import java.io.File;
import java.util.logging.Logger;
import java.util.logging.Level;

public class WekaTrainer {
    private static final Logger LOGGER = Logger.getLogger(WekaTrainer.class.getName());

    public void trainModelWithWeka() {
        try {
            LOGGER.info("Starting model training process...");

            File trainingFile = new File("ddos_training_data.csv");
            if (!trainingFile.exists()) {
                LOGGER.severe("Training data file not found: " + trainingFile.getAbsolutePath());
                throw new RuntimeException("Training data file not found");
            }

            // Load CSV data
            LOGGER.info("Loading training data from CSV...");
            CSVLoader loader = new CSVLoader();
            loader.setSource(trainingFile);
            Instances data = loader.getDataSet();

            // Verify data is loaded correctly
            if (data.numInstances() == 0) {
                LOGGER.severe("No instances found in training data");
                throw new RuntimeException("Empty training dataset");
            }
            LOGGER.info("Loaded " + data.numInstances() + " training instances");

            // Set class index (last attribute)
            data.setClassIndex(data.numAttributes() - 1);

            // Configure and train classifier
            LOGGER.info("Configuring Random Forest classifier...");
            RandomForest classifier = new RandomForest();

            // Set Random Forest parameters
            classifier.setNumIterations(100);
            classifier.setMaxDepth(0);  // unlimited depth
            classifier.setSeed(42);  // for reproducibility
            classifier.setNumExecutionSlots(4);  // parallel processing
            classifier.setComputeAttributeImportance(true);  // compute feature importance
            classifier.setCalcOutOfBag(true);  // Enable out-of-bag error estimation

            LOGGER.info("Training classifier...");
            classifier.buildClassifier(data);

            // Save model
            File modelFile = new File("ddos_model.model");
            LOGGER.info("Saving model to: " + modelFile.getAbsolutePath());
            SerializationHelper.write(modelFile.getPath(), classifier);

            LOGGER.info("Model training completed successfully");

            // Print model summary
            LOGGER.info("Model summary:");
            LOGGER.info("Number of iterations: " + classifier.getNumIterations());
            LOGGER.info("Out of bag error: " + classifier.measureOutOfBagError());
            LOGGER.info("Number of attributes: " + data.numAttributes());

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during model training", e);
            throw new RuntimeException("Failed to train model", e);
        }
    }
}