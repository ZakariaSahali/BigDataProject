package com.incremental.image.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Manifest representing a snapshot of the image collection at a specific point in time.
 */
public class Manifest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String version;
    private long timestamp;
    private String datasetPath;
    // Maps relativePath -> ImageMetadata
    private Map<String, ImageMetadata> images = new HashMap<>();

    public Manifest() {
        this.timestamp = System.currentTimeMillis();
    }

    public Manifest(String version, String datasetPath) {
        this.version = version;
        this.datasetPath = datasetPath;
        this.timestamp = System.currentTimeMillis();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getDatasetPath() {
        return datasetPath;
    }

    public void setDatasetPath(String datasetPath) {
        this.datasetPath = datasetPath;
    }

    public Map<String, ImageMetadata> getImages() {
        return images;
    }

    public void setImages(Map<String, ImageMetadata> images) {
        this.images = images;
    }

    public void addImage(ImageMetadata image) {
        this.images.put(image.getRelativePath(), image);
    }
}
