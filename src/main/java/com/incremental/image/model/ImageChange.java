package com.incremental.image.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Encapsulates the detected change for an image between two manifests.
 */
public class ImageChange implements Serializable {
    private static final long serialVersionUID = 1L;

    private String relativePath;
    private ChangeType changeType;
    private ImageMetadata previousMetadata;
    private ImageMetadata currentMetadata;

    public ImageChange() {
    }

    public ImageChange(String relativePath, ChangeType changeType, ImageMetadata previousMetadata, ImageMetadata currentMetadata) {
        this.relativePath = relativePath;
        this.changeType = changeType;
        this.previousMetadata = previousMetadata;
        this.currentMetadata = currentMetadata;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(ChangeType changeType) {
        this.changeType = changeType;
    }

    public ImageMetadata getPreviousMetadata() {
        return previousMetadata;
    }

    public void setPreviousMetadata(ImageMetadata previousMetadata) {
        this.previousMetadata = previousMetadata;
    }

    public ImageMetadata getCurrentMetadata() {
        return currentMetadata;
    }

    public void setCurrentMetadata(ImageMetadata currentMetadata) {
        this.currentMetadata = currentMetadata;
    }

    @Override
    public String toString() {
        return "ImageChange{" +
                "relativePath='" + relativePath + '\'' +
                ", changeType=" + changeType +
                '}';
    }
}
