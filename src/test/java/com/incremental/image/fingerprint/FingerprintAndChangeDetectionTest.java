package com.incremental.image.fingerprint;

import com.incremental.image.model.ChangeType;
import com.incremental.image.model.ImageChange;
import com.incremental.image.model.Manifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FingerprintAndChangeDetectionTest {

    @TempDir
    Path tempDir;

    private FingerprintService fingerprintService;
    private ManifestManager manifestManager;
    private ChangeDetector changeDetector;

    @BeforeEach
    void setUp() {
        fingerprintService = new FingerprintService();
        manifestManager = new ManifestManager(fingerprintService);
        changeDetector = new ChangeDetector();
    }

    @Test
    void testComputeSha256Consistent() throws IOException {
        Path testFile = tempDir.resolve("test_image.png");
        Files.write(testFile, new byte[]{1, 2, 3, 4, 5});

        String hash1 = fingerprintService.computeSha256(testFile.toFile());
        String hash2 = fingerprintService.computeSha256(testFile.toFile());

        assertNotNull(hash1);
        assertEquals(64, hash1.length(), "SHA-256 hex string should be 64 characters");
        assertEquals(hash1, hash2, "Repeated hash should match");
    }

    @Test
    void testChangeDetectionNewModifiedDeletedUnchanged() throws IOException {
        // Prepare initial collection V1
        Path img1 = tempDir.resolve("unchanged.png");
        Path img2 = tempDir.resolve("to_be_modified.png");
        Path img3 = tempDir.resolve("to_be_deleted.png");

        Files.write(img1, new byte[]{10, 20, 30});
        Files.write(img2, new byte[]{40, 50, 60});
        Files.write(img3, new byte[]{70, 80, 90});

        Manifest manifestV1 = manifestManager.generateManifest(tempDir.toFile(), "v1");
        assertEquals(3, manifestV1.getImages().size());

        // Modify collection for V2
        Files.delete(img3); // DELETED
        Files.write(img2, new byte[]{40, 50, 99}); // MODIFIED (content changed)
        Path img4 = tempDir.resolve("new_file.jpg");
        Files.write(img4, new byte[]{11, 22, 33}); // NEW

        Manifest manifestV2 = manifestManager.generateManifest(tempDir.toFile(), "v2");
        assertEquals(3, manifestV2.getImages().size());

        // Run change detector
        List<ImageChange> changes = changeDetector.detectChangesByFingerprint(manifestV1, manifestV2);
        Map<String, ChangeType> changeMap = changes.stream()
                .collect(Collectors.toMap(ImageChange::getRelativePath, ImageChange::getChangeType));

        assertEquals(ChangeType.UNCHANGED, changeMap.get("unchanged.png"));
        assertEquals(ChangeType.MODIFIED, changeMap.get("to_be_modified.png"));
        assertEquals(ChangeType.DELETED, changeMap.get("to_be_deleted.png"));
        assertEquals(ChangeType.NEW, changeMap.get("new_file.jpg"));
    }

    @Test
    void testManifestSerializationAndDeserialization() throws IOException {
        Path img = tempDir.resolve("sample.png");
        Files.write(img, new byte[]{1, 2, 3});

        Manifest manifest = manifestManager.generateManifest(tempDir.toFile(), "v1");
        File manifestFile = tempDir.resolve("manifest.json").toFile();

        manifestManager.saveManifest(manifest, manifestFile);
        assertTrue(manifestFile.exists());

        Manifest loaded = manifestManager.loadManifest(manifestFile);
        assertEquals(manifest.getVersion(), loaded.getVersion());
        assertEquals(manifest.getImages().size(), loaded.getImages().size());
        assertEquals(manifest.getImages().get("sample.png").getSha256(),
                     loaded.getImages().get("sample.png").getSha256());
    }
}
