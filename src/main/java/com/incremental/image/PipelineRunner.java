package com.incremental.image;

import com.incremental.image.cache.EmbeddingCacheManager;
import com.incremental.image.dl.DL4JFeatureExtractor;
import com.incremental.image.fingerprint.ChangeDetector;
import com.incremental.image.fingerprint.ManifestManager;
import com.incremental.image.model.CacheKey;
import com.incremental.image.model.ImageChange;
import com.incremental.image.model.ImageMetadata;
import com.incremental.image.model.Manifest;
import com.incremental.image.model.EmbeddingRecord;
import com.incremental.image.spark.SparkEmbeddingPipeline;
import com.incremental.image.spark.SparkPipelineContext;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Runner application with rich dashboard metrics:
 * counts total images, previously cached (old), new/unseen images,
 * and displays a clean visual summary benchmark with timers and speedup calculations.
 */
public class PipelineRunner {

    public static void main(String[] args) throws IOException {
        System.out.println("================================================================================");
        System.out.println("     INCREMENTAL DEEP LEARNING FEATURE EXTRACTION PIPELINE RUNNER");
        System.out.println("================================================================================\n");

        File datasetDir = new File("data/images");
        if (!datasetDir.exists() || !datasetDir.isDirectory()) {
            System.err.println("Dataset directory not found: " + datasetDir.getAbsolutePath());
            return;
        }

        File cacheDir = new File("cache");
        File manifestDir = new File("data/manifests");
        manifestDir.mkdirs();

        ManifestManager manifestManager = new ManifestManager();
        ChangeDetector changeDetector = new ChangeDetector();
        EmbeddingCacheManager cacheManager = new EmbeddingCacheManager(cacheDir);

        // 1. Scan Dataset
        System.out.println("🔍 [1/3] Scanning dataset directory: " + datasetDir.getAbsolutePath());
        Manifest currentManifest = manifestManager.generateManifest(datasetDir, "current");
        File manifestOut = new File(manifestDir, "manifest_current.json");
        manifestManager.saveManifest(currentManifest, manifestOut);

        int totalFound = currentManifest.getImages().size();

        // 2. Inspect Cache Status before execution (Old vs New)
        int alreadyCachedCount = 0;
        int newUncachedCount = 0;
        for (ImageMetadata meta : currentManifest.getImages().values()) {
            CacheKey key = new CacheKey(
                    meta.getSha256(),
                    DL4JFeatureExtractor.DEFAULT_MODEL_VERSION,
                    DL4JFeatureExtractor.DEFAULT_PREPROCESSING_VERSION
            );
            if (cacheManager.contains(key)) {
                alreadyCachedCount++;
            } else {
                newUncachedCount++;
            }
        }

        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(" 📊 DATASET INVENTORY BREAKDOWN");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("  • Total Images Discovered : %d\n", totalFound);
        System.out.printf("  • Already Cached (Old)    : %d (skips deep learning inference)\n", alreadyCachedCount);
        System.out.printf("  • New / Unseen Images     : %d (requires neural network inference)\n", newUncachedCount);
        System.out.printf("  • Cache Warmth            : %.1f%%\n", totalFound > 0 ? (alreadyCachedCount * 100.0 / totalFound) : 0.0);
        System.out.println("--------------------------------------------------------------------------------\n");

        // 3. Detect Changes
        List<ImageChange> changes = changeDetector.detectChangesByFingerprint(null, currentManifest);

        // 4. Distributed Spark Execution
        System.out.println("⚡ [2/3] Starting Apache Spark (local[1]) & DL4J VGG16 Pipeline...\n");

        long startExecution = System.currentTimeMillis();
        long run1Duration;
        long run2Duration;
        int initialCacheHits = (int) cacheManager.getStats().getHits();
        int initialCacheMisses = (int) cacheManager.getStats().getMisses();

        try (SparkPipelineContext sparkContext = new SparkPipelineContext("Image-Embedding-Runner", "local[1]", 1)) {
            DL4JFeatureExtractor extractor = new DL4JFeatureExtractor();
            SparkEmbeddingPipeline pipeline = new SparkEmbeddingPipeline(sparkContext, extractor, cacheManager, datasetDir);

            // RUN 1: Process current changes
            System.out.println(">>> Executing Run 1: Active Ingestion & Processing...");
            long t1 = System.currentTimeMillis();
            Map<String, EmbeddingRecord> results1 = pipeline.process(
                    changes, SparkEmbeddingPipeline.ExecutionMode.FINGERPRINT_INCREMENTAL
            );
            run1Duration = System.currentTimeMillis() - t1;

            // RUN 2: Re-run on unchanged collection to demonstrate incremental speedup
            System.out.println(">>> Executing Run 2: Re-run Check (Unchanged Collection Benchmark)...");
            List<ImageChange> unchangedCheck = changeDetector.detectChangesByFingerprint(currentManifest, currentManifest);
            long t2 = System.currentTimeMillis();
            Map<String, EmbeddingRecord> results2 = pipeline.process(
                    unchangedCheck, SparkEmbeddingPipeline.ExecutionMode.FINGERPRINT_INCREMENTAL
            );
            run2Duration = System.currentTimeMillis() - t2;
        }

        long totalElapsed = System.currentTimeMillis() - startExecution;
        double speedup = (double) Math.max(run1Duration, 1) / (double) Math.max(run2Duration, 1);

        // 5. Clean, Professional Showcase Dashboard
        System.out.println("\n");
        System.out.println("================================================================================");
        System.out.println("                   🎉 PIPELINE EXECUTION SHOWCASE REPORT");
        System.out.println("================================================================================");
        System.out.printf(" 📁 Dataset Location        : %s\n", datasetDir.getPath());
        System.out.printf(" 🖼️  Total Images Found      : %d\n", totalFound);
        System.out.printf(" 📦 Old (Cached) Images     : %d\n", alreadyCachedCount);
        System.out.printf(" 🆕 New (Inferred) Images   : %d\n", newUncachedCount);
        System.out.printf(" 💾 Total Cached Records    : %d binary embeddings in cache/\n", cacheManager.size());
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(" ⏱️  PERFORMANCE & BENCHMARK TIMINGS");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("  • Run 1 (Active Ingestion): %7d ms  (%.2f s)\n", run1Duration, run1Duration / 1000.0);
        System.out.printf("  • Run 2 (100%% Incremental): %7d ms  (%.2f s)\n", run2Duration, run2Duration / 1000.0);
        System.out.printf("  • Total Workflow Runtime  : %7d ms  (%.2f s)\n", totalElapsed, totalElapsed / 1000.0);
        System.out.printf("  • Measured Speedup Factor : %7.1fx FASTER\n", speedup);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(" 📈 CACHE EFFICIENCY METRICS");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("  • Total Requests          : %d\n", cacheManager.getStats().getTotalRequests());
        System.out.printf("  • Cache Hits              : %d\n", cacheManager.getStats().getHits());
        System.out.printf("  • Cache Misses            : %d\n", cacheManager.getStats().getMisses());
        System.out.printf("  • Global Cache Hit Rate   : %.2f%%\n", cacheManager.getStats().getHitRate() * 100);
        System.out.println("================================================================================");
        System.out.println("                         STATUS: ALL SYSTEMS OPERATIONAL");
        System.out.println("================================================================================\n");
    }
}
