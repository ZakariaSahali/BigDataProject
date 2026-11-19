package com.incremental.image.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents an image embedding record stored in the persistent cache.
 */
public class EmbeddingRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private CacheKey cacheKey;
    private float[] vector;
    private int dimension;
    private long createdAt;

    public EmbeddingRecord() {
        this.createdAt = System.currentTimeMillis();
    }

    public EmbeddingRecord(CacheKey cacheKey, float[] vector) {
        this.cacheKey = Objects.requireNonNull(cacheKey, "cacheKey cannot be null");
        this.vector = Objects.requireNonNull(vector, "vector cannot be null");
        this.dimension = vector.length;
        this.createdAt = System.currentTimeMillis();
    }

    public EmbeddingRecord(CacheKey cacheKey, float[] vector, long createdAt) {
        this.cacheKey = Objects.requireNonNull(cacheKey, "cacheKey cannot be null");
        this.vector = Objects.requireNonNull(vector, "vector cannot be null");
        this.dimension = vector.length;
        this.createdAt = createdAt;
    }

    public CacheKey getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(CacheKey cacheKey) {
        this.cacheKey = cacheKey;
    }

    public float[] getVector() {
        return vector;
    }

    public void setVector(float[] vector) {
        this.vector = vector;
        this.dimension = vector != null ? vector.length : 0;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingRecord that = (EmbeddingRecord) o;
        return dimension == that.dimension &&
               createdAt == that.createdAt &&
               Objects.equals(cacheKey, that.cacheKey) &&
               Arrays.equals(vector, that.vector);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(cacheKey, dimension, createdAt);
        result = 31 * result + Arrays.hashCode(vector);
        return result;
    }

    @Override
    public String toString() {
        return "EmbeddingRecord{" +
                "cacheKey=" + cacheKey +
                ", dimension=" + dimension +
                ", createdAt=" + createdAt +
                '}';
    }
}
