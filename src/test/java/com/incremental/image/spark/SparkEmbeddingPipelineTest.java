package com.incremental.image.spark;

import com.incremental.image.cache.EmbeddingCacheManager;
import com.incremental.image.dl.DL4JFeatureExtractor;
import com.incremental.image.fingerprint.ChangeDetector;
import com.incremental.image.fingerprint.ManifestManager;
import com.incremental.image.model.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SparkEmbeddingPipelineTest {

    @TempDir
    static Path tempDir;

    private static SparkPipelineContext sparkContext;
    private static File datasetDir;
    private static File cacheDir;
    private static ManifestManager manifestManager;
    private static ChangeDetector changeDetector;

    @BeforeAll
    static void init() throws IOException {
        sparkContext = new SparkPipelineContext("Test-Spark-Pipeline", "local[2]", 2);
        datasetDir = tempDir.resolve("images").toFile();
        datasetDir.mkdirs();
        cacheDir = tempDir.resolve("cache").toFile();
        cacheDir.mkdirs();

        manifestManager = new ManifestManager();
        changeDetector = new ChangeDetector();

        // Create 4 synthetic test images
        for (int i = 1; i <= 4; i++) {
            createTestImage(new File(datasetDir, "image_" + i + ".jpg"), i * 40);
        }
    }

    @AfterAll
    static void tearDown() {
        if (sparkContext != null) {
            sparkContext.close();
        }
    }

    private static void createTestImage(File file, int colorOffset) throws IOException {
        BufferedImage img = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(Math.min(255, 50 + colorOffset), 100, 150));
        g.fillRect(0, 0, 224, 224);
        g.dispose();
        ImageIO.write(img, "jpg", file);
    }

    @Test
    void testIncrementalVsFullExecutionSparkRDD() throws IOException {
        EmbeddingCacheManager cacheManager = new EmbeddingCacheManager(cacheDir);

        // Lightweight extractor for fast testing
        DL4JFeatureExtractor extractor = new DL4JFeatureExtractor(
                "vgg16-test-v1", "vgg16-test-prep", "fc2", 224, 224, 3
        );

        SparkEmbeddingPipeline pipeline = new SparkEmbeddingPipeline(
                sparkContext, extractor, cacheManager, datasetDir
        );

        // Generate V1 manifest
        Manifest v1Manifest = manifestManager.generateManifest(datasetDir, "v1");
        List<ImageChange> initialChanges = changeDetector.detectChangesByFingerprint(null, v1Manifest);
        assertEquals(4, initialChanges.size());

        // --- Run 1: First time processing with FINGERPRINT_INCREMENTAL ---
        // Cache is empty -> 4 misses, 4 extracted via Spark RDD
        Map<String, EmbeddingRecord> resultsV1 = pipeline.process(
                initialChanges, SparkEmbeddingPipeline.ExecutionMode.FINGERPRINT_INCREMENTAL
        );
        assertEquals(4, resultsV1.size());
        assertEquals(4, cacheManager.size(), "Cache should now contain 4 embeddings");
        assertEquals(4, cacheManager.getStats().getMisses());
        assertEquals(0, cacheManager.getStats().getHits());

        // --- Run 2: Unchanged dataset re-run ---
        // Manifest V1 vs V1 -> all 4 are UNCHANGED -> 4 cache hits, 0 Spark RDD tasks needed!
        List<ImageChange> noChanges = changeDetector.detectChangesByFingerprint(v1Manifest, v1Manifest);
        Map<String, EmbeddingRecord> resultsUnchanged = pipeline.process(
                noChanges, SparkEmbeddingPipeline.ExecutionMode.FINGERPRINT_INCREMENTAL
        );

        assertEquals(4, resultsUnchanged.size());
        assertEquals(4, cacheManager.getStats().getHits(), "All 4 should be cache hits!");
        assertTrue(cacheManager.getStats().getHitRate() >= 0.5, "Hit rate should be >= 50%");
    }
}
