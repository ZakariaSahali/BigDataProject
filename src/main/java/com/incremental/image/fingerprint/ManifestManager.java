package com.incremental.image.fingerprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.incremental.image.model.ImageMetadata;
import com.incremental.image.model.Manifest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Manages manifest generation, saving, and loading from disk in JSON format.
 */
public class ManifestManager {

    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(
            Arrays.asList(".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp")
    );

    private final FingerprintService fingerprintService;
    private final ObjectMapper objectMapper;

    public ManifestManager() {
        this(new FingerprintService());
    }

    public ManifestManager(FingerprintService fingerprintService) {
        this.fingerprintService = fingerprintService;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Scan an image directory and produce a complete Manifest with SHA-256 fingerprints.
     */
    public Manifest generateManifest(File baseDirectory, String version) throws IOException {
        if (!baseDirectory.exists() || !baseDirectory.isDirectory()) {
            throw new IllegalArgumentException("Base directory does not exist or is not a directory: " + baseDirectory);
        }

        Manifest manifest = new Manifest(version, baseDirectory.getAbsolutePath());
        Path basePath = baseDirectory.toPath();

        try (Stream<Path> stream = Files.walk(basePath)) {
            stream.filter(Files::isRegularFile)
                  .filter(this::isImageFile)
                  .forEach(path -> {
                      try {
                          File file = path.toFile();
                          String relativePath = basePath.relativize(path).toString().replace('\\', '/');
                          String sha256 = fingerprintService.computeSha256(file);
                          long lastModified = file.lastModified();
                          long sizeBytes = file.length();

                          ImageMetadata metadata = new ImageMetadata(relativePath, sha256, lastModified, sizeBytes);
                          manifest.addImage(metadata);
                      } catch (IOException e) {
                          throw new RuntimeException("Failed to fingerprint file: " + path, e);
                      }
                  });
        }

        return manifest;
    }

    /**
     * Save manifest to a JSON file.
     */
    public void saveManifest(Manifest manifest, File outputFile) throws IOException {
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }
        objectMapper.writeValue(outputFile, manifest);
    }

    /**
     * Load manifest from a JSON file.
     */
    public Manifest loadManifest(File inputFile) throws IOException {
        return objectMapper.readValue(inputFile, Manifest.class);
    }

    private boolean isImageFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (filename.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
