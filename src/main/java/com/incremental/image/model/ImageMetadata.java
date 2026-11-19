package com.incremental.image.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Metadata recorded for an individual image file.
 */
public class ImageMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private String relativePath;
    private String sha256;
    private long lastModified;
    private long sizeBytes;

    public ImageMetadata() {
    }

    public ImageMetadata(String relativePath, String sha256, long lastModified, long sizeBytes) {
        this.relativePath = relativePath;
        this.sha256 = sha256;
        this.lastModified = lastModified;
        this.sizeBytes = sizeBytes;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImageMetadata that = (ImageMetadata) o;
        return lastModified == that.lastModified &&
               sizeBytes == that.sizeBytes &&
               Objects.equals(relativePath, that.relativePath) &&
               Objects.equals(sha256, that.sha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativePath, sha256, lastModified, sizeBytes);
    }

    @Override
    public String toString() {
        return "ImageMetadata{" +
                "relativePath='" + relativePath + '\'' +
                ", sha256='" + sha256 + '\'' +
                ", lastModified=" + lastModified +
                ", sizeBytes=" + sizeBytes +
                '}';
    }
}
