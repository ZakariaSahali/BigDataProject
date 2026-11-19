package com.incremental.image.dl;

import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DL4JFeatureExtractorTest {

    @TempDir
    Path tempDir;

    private File testImageFile;
    private ComputationGraph lightweightGraph;
    private DL4JFeatureExtractor extractor;

    @BeforeEach
    void setUp() throws IOException {
        // 1. Create a dummy synthetic test image (224x224 RGB)
        testImageFile = tempDir.resolve("synthetic_test.jpg").toFile();
        BufferedImage img = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, 224, 224);
        g2d.setColor(Color.RED);
        g2d.fillOval(50, 50, 100, 100);
        g2d.dispose();
        ImageIO.write(img, "jpg", testImageFile);

        // 2. Build a fast, lightweight ComputationGraph with a custom embedding layer
        // to unit-test pipeline, activations, and dimensions without downloading 500MB VGG weights in CI
        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(12345)
                .graphBuilder()
                .addInputs("input")
                .setInputTypes(org.deeplearning4j.nn.conf.inputs.InputType.convolutional(224, 224, 3))
                .addLayer("dense_embedding", new DenseLayer.Builder()
                        .nOut(128)
                        .activation(Activation.RELU)
                        .build(), "input")
                .addLayer("output", new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .nOut(10)
                        .activation(Activation.IDENTITY)
                        .build(), "dense_embedding")
                .setOutputs("output")
                .build();

        lightweightGraph = new ComputationGraph(conf);
        lightweightGraph.init();

        extractor = new DL4JFeatureExtractor(
                lightweightGraph,
                "test-lightweight-v1",
                "test-prep-224x224",
                "dense_embedding",
                224, 224, 3
        );
    }

    @Test
    void testFeatureExtractionDimensionsAndConsistency() throws IOException {
        float[] embedding1 = extractor.extractEmbedding(testImageFile);
        assertNotNull(embedding1);
        assertEquals(128, embedding1.length, "Embedding dimension should match intermediate layer nOut");

        // Test determinism (same image -> identical embedding)
        float[] embedding2 = extractor.extractEmbedding(testImageFile);
        assertArrayEquals(embedding1, embedding2, 1e-6f, "Repeated inference on same image must be deterministic");
    }

    @Test
    void testExtractorMetadata() {
        assertEquals("test-lightweight-v1", extractor.getModelVersion());
        assertEquals("test-prep-224x224", extractor.getPreprocessingVersion());
        assertEquals("dense_embedding", extractor.getFeatureLayer());
    }
}
