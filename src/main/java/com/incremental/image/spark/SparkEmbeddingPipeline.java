package com.incremental.image.spark;

import com.incremental.image.cache.EmbeddingCacheManager;
import com.incremental.image.dl.DL4JFeatureExtractor;
import com.incremental.image.model.CacheKey;
import com.incremental.image.model.ChangeType;
import com.incremental.image.model.EmbeddingRecord;
import com.incremental.image.model.ImageChange;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates distributed feature embedding extraction using Apache Spark Core RDDs.
 * 
 * Supports 3 experimental execution modes:
 *  1. FULL_RECALCULATION: Forces RDD re-extraction of every image regardless of cache.
 *  2. TIMESTAMP_INCREMENTAL: Skips images using timestamp comparison.
 *  3. FINGERPRINT_INCREMENTAL: Skips images using SHA-256 fingerprint cache hits,
 *     distributing only NEW and MODIFIED images across Spark partitions using mapPartitions.
 */
public class SparkEmbeddingPipeline implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum ExecutionMode {
        FULL_RECALCULATION,
        TIMESTAMP_INCREMENTAL,
        FINGERPRINT_INCREMENTAL
    }

    private final SparkPipelineContext sparkContextManager;
    private final DL4JFeatureExtractor featureExtractor;
    private final EmbeddingCacheManager cacheManager;
    private final File baseDatasetDir;

    public SparkEmbeddingPipeline(SparkPipelineContext sparkContextManager,
                                  DL4JFeatureExtractor featureExtractor,
                                  EmbeddingCacheManager cacheManager,
                                  File baseDatasetDir) {
        this.sparkContextManager = Objects.requireNonNull(sparkContextManager, "sparkContextManager cannot be null");
        this.featureExtractor = Objects.requireNonNull(featureExtractor, "featureExtractor cannot be null");
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager cannot be null");
        this.baseDatasetDir = Objects.requireNonNull(baseDatasetDir, "baseDatasetDir cannot be null");
    }

    /**
     * Executes the pipeline over a list of image changes according to the selected ExecutionMode.
     *
     * @param imageChanges list of detected image changes
     * @param mode         execution approach to test/run
     * @return map of relativePath -> EmbeddingRecord
     */
    public Map<String, EmbeddingRecord> process(List<ImageChange> imageChanges, ExecutionMode mode) throws IOException {
        Map<String, EmbeddingRecord> finalResults = new HashMap<>();
        List<ImageChange> toExtract = new ArrayList<>();

        String modelVer = featureExtractor.getModelVersion();
        String prepVer = featureExtractor.getPreprocessingVersion();

        // 1. Filter images based on mode and cache lookups
        for (ImageChange change : imageChanges) {
            if (change.getChangeType() == ChangeType.DELETED) {
                // Deleted images do not need embeddings in the current snapshot
                continue;
            }

            if (mode == ExecutionMode.FULL_RECALCULATION) {
                toExtract.add(change);
            } else if (mode == ExecutionMode.TIMESTAMP_INCREMENTAL) {
                // If timestamp didn't change, try cache
                if (change.getChangeType() == ChangeType.UNCHANGED) {
                    CacheKey key = new CacheKey(change.getCurrentMetadata().getSha256(), modelVer, prepVer);
                    Optional<EmbeddingRecord> cached = cacheManager.get(key);
                    if (cached.isPresent()) {
                        finalResults.put(change.getRelativePath(), cached.get());
                    } else {
                        toExtract.add(change);
                    }
                } else {
                    toExtract.add(change);
                }
            } else if (mode == ExecutionMode.FINGERPRINT_INCREMENTAL) {
                // Incremental by SHA-256 fingerprint
                CacheKey key = new CacheKey(change.getCurrentMetadata().getSha256(), modelVer, prepVer);
                Optional<EmbeddingRecord> cached = cacheManager.get(key);

                if (cached.isPresent()) {
                    finalResults.put(change.getRelativePath(), cached.get());
                } else {
                    toExtract.add(change);
                }
            }
        }

        // 2. If all images were cache hits, return immediately
        if (toExtract.isEmpty()) {
            return finalResults;
        }

        // 3. Parallelize remaining images into a Spark JavaRDD
        JavaSparkContext sc = sparkContextManager.getOrCreate();
        int numPartitions = Math.min(toExtract.size(), sparkContextManager.getDefaultPartitions());
        numPartitions = Math.max(1, numPartitions);

        JavaRDD<ImageChange> changesRDD = sc.parallelize(toExtract, numPartitions);

        // Capture local variables needed in worker partitions
        File datasetDir = this.baseDatasetDir;
        DL4JFeatureExtractor extractorTemplate = this.featureExtractor;

        // 4. Distribute using mapPartitions (loads neural net ONCE per worker partition)
        JavaRDD<EmbeddingRecord> computedRDD = changesRDD.mapPartitions(partitionIterator -> {
            // Worker-side initialization
            DL4JFeatureExtractor localExtractor = new DL4JFeatureExtractor(
                    extractorTemplate.getModelVersion(),
                    extractorTemplate.getPreprocessingVersion(),
                    extractorTemplate.getFeatureLayer(),
                    extractorTemplate.getTargetHeight(),
                    extractorTemplate.getTargetWidth(),
                    extractorTemplate.getChannels()
            );
            localExtractor.initializeIfNeeded();

            List<EmbeddingRecord> partitionResults = new ArrayList<>();
            while (partitionIterator.hasNext()) {
                ImageChange ic = partitionIterator.next();
                File imgFile = new File(datasetDir, ic.getRelativePath());

                float[] vector = localExtractor.extractEmbedding(imgFile);
                CacheKey key = new CacheKey(
                        ic.getCurrentMetadata().getSha256(),
                        localExtractor.getModelVersion(),
                        localExtractor.getPreprocessingVersion()
                );

                EmbeddingRecord record = new EmbeddingRecord(key, vector);
                partitionResults.add(record);
            }
            return partitionResults.iterator();
        });

        // 5. Collect distributed results back to driver
        List<EmbeddingRecord> newlyComputed = computedRDD.collect();

        // 6. Persist newly computed embeddings into cache and assemble final results
        for (int i = 0; i < toExtract.size(); i++) {
            ImageChange ic = toExtract.get(i);
            EmbeddingRecord record = newlyComputed.get(i);

            // Persist to cache
            cacheManager.put(record);
            finalResults.put(ic.getRelativePath(), record);
        }

        return finalResults;
    }

    public SparkPipelineContext getSparkContextManager() {
        return sparkContextManager;
    }

    public DL4JFeatureExtractor getFeatureExtractor() {
        return featureExtractor;
    }

    public EmbeddingCacheManager getCacheManager() {
        return cacheManager;
    }
}
