package com.incremental.image.dl;

import org.datavec.image.loader.NativeImageLoader;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.zoo.ZooModel;
import org.deeplearning4j.zoo.model.VGG16;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.VGG16ImagePreProcessor;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * Feature extractor utilizing Deeplearning4j pre-trained models (e.g. VGG16)
 * to produce high-dimensional embedding representations of images.
 */
public class DL4JFeatureExtractor implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_MODEL_NAME = "VGG16";
    public static final String DEFAULT_MODEL_VERSION = "vgg16-imagenet-v1";
    public static final String DEFAULT_PREPROCESSING_VERSION = "vgg16-bgr-subtraction-224x224";
    public static final String DEFAULT_FEATURE_LAYER = "fc2"; // VGG16 layer producing 4096-dim vector

    private final String modelVersion;
    private final String preprocessingVersion;
    private final String featureLayer;
    private final int targetHeight;
    private final int targetWidth;
    private final int channels;

    private transient ComputationGraph computationGraph;
    private transient NativeImageLoader imageLoader;
    private transient DataNormalization preProcessor;

    public DL4JFeatureExtractor() {
        this(DEFAULT_MODEL_VERSION, DEFAULT_PREPROCESSING_VERSION, DEFAULT_FEATURE_LAYER, 224, 224, 3);
    }

    public DL4JFeatureExtractor(ComputationGraph computationGraph, String modelVersion,
                                String preprocessingVersion, String featureLayer,
                                int height, int width, int channels) {
        this.computationGraph = computationGraph;
        this.modelVersion = modelVersion;
        this.preprocessingVersion = preprocessingVersion;
        this.featureLayer = featureLayer;
        this.targetHeight = height;
        this.targetWidth = width;
        this.channels = channels;
        initHelpers();
    }

    public DL4JFeatureExtractor(String modelVersion, String preprocessingVersion,
                                String featureLayer, int height, int width, int channels) {
        this.modelVersion = modelVersion;
        this.preprocessingVersion = preprocessingVersion;
        this.featureLayer = featureLayer;
        this.targetHeight = height;
        this.targetWidth = width;
        this.channels = channels;
    }

    /**
     * Lazy initialization of deep learning model and preprocessors
     * (crucial for Spark worker serialization across nodes).
     */
    public synchronized void initializeIfNeeded() throws IOException {
        if (computationGraph == null) {
            ZooModel zooModel = VGG16.builder().build();
            this.computationGraph = (ComputationGraph) zooModel.initPretrained();
        }
        initHelpers();
    }

    private void initHelpers() {
        if (imageLoader == null) {
            this.imageLoader = new NativeImageLoader(targetHeight, targetWidth, channels);
        }
        if (preProcessor == null) {
            this.preProcessor = new VGG16ImagePreProcessor();
        }
    }

    /**
     * Extract embedding vector from an image file.
     *
     * @param imageFile the input image file
     * @return float array representing the feature embedding
     * @throws IOException if image reading or model inference fails
     */
    public float[] extractEmbedding(File imageFile) throws IOException {
        Objects.requireNonNull(imageFile, "Image file cannot be null");
        if (!imageFile.exists() || !imageFile.isFile()) {
            throw new IllegalArgumentException("File does not exist: " + imageFile.getAbsolutePath());
        }

        initializeIfNeeded();

        // 1. Load image into INDArray [1, channels, height, width]
        INDArray image = imageLoader.asMatrix(imageFile);

        // 2. Apply preprocessing
        preProcessor.transform(image);

        // 3. Extract intermediate layer activation
        Map<String, INDArray> activations = computationGraph.feedForward(image, false);
        INDArray featureMap = activations.get(featureLayer);

        if (featureMap == null) {
            throw new IllegalStateException("Feature layer '" + featureLayer + "' not found in computation graph");
        }

        // 4. Flatten to 1D float array
        INDArray flat = featureMap.reshape(1, featureMap.length());
        return flat.toFloatVector();
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getPreprocessingVersion() {
        return preprocessingVersion;
    }

    public String getFeatureLayer() {
        return featureLayer;
    }

    public int getTargetHeight() {
        return targetHeight;
    }

    public int getTargetWidth() {
        return targetWidth;
    }

    public int getChannels() {
        return channels;
    }
}
