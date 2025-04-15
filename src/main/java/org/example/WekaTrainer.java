package org.example;

import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.core.SerializationHelper;

import java.io.File;

public class WekaTrainer {

    public void trainModelWithWeka() {
        try {
            // Load CSV data
            CSVLoader loader = new CSVLoader();
            loader.setSource(new File("ddos_training_data.csv"));
            Instances data = loader.getDataSet();

            // Set class index (last attribute)
            data.setClassIndex(data.numAttributes() - 1);

            // Create and train classifier
            RandomForest classifier = new RandomForest();
            classifier.setNumIterations(100);  // Correct method name
            classifier.buildClassifier(data);

            // Save model
            SerializationHelper.write("ddos_model.model", classifier);
            System.out.println("Model saved to ddos_model.model");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
