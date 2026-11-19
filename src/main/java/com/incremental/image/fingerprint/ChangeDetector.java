package com.incremental.image.fingerprint;

import com.incremental.image.model.ChangeType;
import com.incremental.image.model.ImageChange;
import com.incremental.image.model.ImageMetadata;
import com.incremental.image.model.Manifest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compares two Manifests to detect changes:
 * - NEW: image exists in current but not in previous
 * - DELETED: image existed in previous but not in current
 * - MODIFIED: image exists in both, but SHA-256 (or timestamp) differs
 * - UNCHANGED: image exists in both with matching fingerprint
 */
public class ChangeDetector {

    /**
     * Detect changes using SHA-256 fingerprint comparison (Approach 3).
     */
    public List<ImageChange> detectChangesByFingerprint(Manifest previousManifest, Manifest currentManifest) {
        List<ImageChange> changes = new ArrayList<>();
        Map<String, ImageMetadata> prevImages = previousManifest != null ? previousManifest.getImages() : Map.of();
        Map<String, ImageMetadata> currImages = currentManifest != null ? currentManifest.getImages() : Map.of();

        // Check for NEW, MODIFIED, and UNCHANGED
        for (Map.Entry<String, ImageMetadata> entry : currImages.entrySet()) {
            String path = entry.getKey();
            ImageMetadata currentMeta = entry.getValue();

            if (!prevImages.containsKey(path)) {
                changes.add(new ImageChange(path, ChangeType.NEW, null, currentMeta));
            } else {
                ImageMetadata prevMeta = prevImages.get(path);
                if (currentMeta.getSha256().equals(prevMeta.getSha256())) {
                    changes.add(new ImageChange(path, ChangeType.UNCHANGED, prevMeta, currentMeta));
                } else {
                    changes.add(new ImageChange(path, ChangeType.MODIFIED, prevMeta, currentMeta));
                }
            }
        }

        // Check for DELETED
        for (Map.Entry<String, ImageMetadata> entry : prevImages.entrySet()) {
            String path = entry.getKey();
            if (!currImages.containsKey(path)) {
                changes.add(new ImageChange(path, ChangeType.DELETED, entry.getValue(), null));
            }
        }

        return changes;
    }

    /**
     * Detect changes using Timestamp-only comparison (Approach 2 - for baseline comparison).
     */
    public List<ImageChange> detectChangesByTimestamp(Manifest previousManifest, Manifest currentManifest) {
        List<ImageChange> changes = new ArrayList<>();
        Map<String, ImageMetadata> prevImages = previousManifest != null ? previousManifest.getImages() : Map.of();
        Map<String, ImageMetadata> currImages = currentManifest != null ? currentManifest.getImages() : Map.of();

        for (Map.Entry<String, ImageMetadata> entry : currImages.entrySet()) {
            String path = entry.getKey();
            ImageMetadata currentMeta = entry.getValue();

            if (!prevImages.containsKey(path)) {
                changes.add(new ImageChange(path, ChangeType.NEW, null, currentMeta));
            } else {
                ImageMetadata prevMeta = prevImages.get(path);
                if (currentMeta.getLastModified() != prevMeta.getLastModified()) {
                    changes.add(new ImageChange(path, ChangeType.MODIFIED, prevMeta, currentMeta));
                } else {
                    changes.add(new ImageChange(path, ChangeType.UNCHANGED, prevMeta, currentMeta));
                }
            }
        }

        for (Map.Entry<String, ImageMetadata> entry : prevImages.entrySet()) {
            String path = entry.getKey();
            if (!currImages.containsKey(path)) {
                changes.add(new ImageChange(path, ChangeType.DELETED, entry.getValue(), null));
            }
        }

        return changes;
    }
}
