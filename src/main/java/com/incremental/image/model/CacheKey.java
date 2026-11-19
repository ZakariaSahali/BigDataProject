package com.incremental.image.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Unique key identifying an embedding in the cache.
 * Per specification: must include Image Fingerprint, Model Version, and Preprocessing Version
 * to prevent incompatible embedding re-use across configurations.
 */
public class CacheKey implements Serializable {
    private static final long serialVersionUID = 1L;

    private String fingerprint;
    private String modelVersion;
    private String preprocessingVersion;

    public CacheKey() {
    }

    public CacheKey(String fingerprint, String modelVersion, String preprocessingVersion) {
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint cannot be null");
        this.modelVersion = Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
        this.preprocessingVersion = Objects.requireNonNull(preprocessingVersion, "preprocessingVersion cannot be null");
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getPreprocessingVersion() {
        return preprocessingVersion;
    }

    public void setPreprocessingVersion(String preprocessingVersion) {
        this.preprocessingVersion = preprocessingVersion;
    }

    /**
     * String representation used as a storage key (e.g., file name or DB key).
     */
    public String toStorageKey() {
        return fingerprint + "_" + modelVersion + "_" + preprocessingVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheKey cacheKey = (CacheKey) o;
        return Objects.equals(fingerprint, cacheKey.fingerprint) &&
               Objects.equals(modelVersion, cacheKey.modelVersion) &&
               Objects.equals(preprocessingVersion, cacheKey.preprocessingVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fingerprint, modelVersion, preprocessingVersion);
    }

    @Override
    public String toString() {
        return "CacheKey{" +
                "fingerprint='" + fingerprint + '\'' +
                ", modelVersion='" + modelVersion + '\'' +
                ", preprocessingVersion='" + preprocessingVersion + '\'' +
                '}';
    }
}
