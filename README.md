# Incremental DL Feature Embedding for Repeated Image Collections

> **Big Data University Project**  
> **Technologies:** Java 11, Apache Spark Core (RDDs only), Deeplearning4j (DL4J), Maven, Git.

---

## 📌 Project Overview (For Beginners)

When working with large collections of images (such as Caltech-101) in real-world scenarios, datasets change over time:
- New images are added.
- Existing images are updated or replaced.
- Outdated images are removed.
- **Most images remain completely unchanged.**

Extracting deep learning feature vectors (embeddings) using neural networks like **VGG16** or **ResNet** is computationally expensive. Running feature extraction on the entire collection every time a change happens wastes massive amounts of CPU/GPU time and power.

### Our Solution
This project builds an **Incremental Feature Extraction Pipeline**:
1. **Snapshots & Fingerprints:** Calculates SHA-256 cryptographic fingerprints for every image and saves them in a lightweight JSON manifest.
2. **Change Detection:** Automatically compares the new dataset version against the previous version to find what actually changed (`NEW`, `MODIFIED`, `DELETED`, `UNCHANGED`).
3. **Smart Persistent Caching:** Reuses previously computed embeddings for unchanged images, skipping expensive neural network inference.
4. **Distributed Processing:** Uses **Apache Spark Core RDDs** to distribute the extraction across worker cores.
5. **Strict Cache Isolation:** Embeddings are cached using a composite key: `(image_sha256, model_version, preprocessing_version)`. If you switch models or change image resizing rules, incompatible cached embeddings will never be mistakenly reused.

---

## 🏗️ Architecture & What Has Been Built So Far

The project is structured under `src/main/java/com/incremental/image/`:

```
com/incremental/image/
├── model/           # Core Data Entities
│   ├── CacheKey.java          # Composite key: (fingerprint, modelVersion, preprocessingVersion)
│   ├── ImageMetadata.java     # Tracks relative path, SHA-256, last modified, byte size
│   ├── Manifest.java          # JSON snapshot of a dataset version at a point in time
│   ├── ChangeType.java        # Enum: NEW, MODIFIED, DELETED, UNCHANGED
│   ├── ImageChange.java       # Encapsulates a detected image change
│   └── EmbeddingRecord.java   # Holds the cache key, float[] vector, dimension & timestamp
│
├── fingerprint/     # Hashing & Change Detection Engine
│   ├── FingerprintService.java# Low-memory streaming SHA-256 hash generator (64KB buffer)
│   ├── ManifestManager.java   # Scans directory trees, generates and saves JSON manifests
│   └── ChangeDetector.java    # Detects diffs using SHA-256 (Approach 3) or Timestamp (Approach 2)
│
├── cache/           # Persistent Storage & Metrics
│   ├── CacheStats.java        # Real-time metrics: hits, misses, hit rate (%), total requests
│   └── EmbeddingCacheManager.java # High-performance binary disk storage (.bin) + memory lookup
│
├── dl/              # Deep Learning Feature Extractor
│   └── DL4JFeatureExtractor.java  # Preprocessing + Deeplearning4j intermediate layer extraction
│
├── spark/           # (Next Phase) Apache Spark Core RDD distributed pipeline
└── benchmark/       # (Next Phase) Comparison of Full vs. Timestamp vs. Incremental processing
```

---

## 🚀 How to Run and Test (For Team Members)

### Prerequisites
Make sure you have installed:
- **Java 11** (`java -version`)
- **Maven 3.8+** (`mvn -v`)
- **Git**

### Clone the Repository
```bash
git clone git@github.com:ZakariaSahali/BigDataProject.git
cd BigDataProject
```

### Build & Run All Tests
To compile the application and execute all unit test suites:
```bash
mvn clean test
```

### What the Automated Tests Verify:
1. `FingerprintAndChangeDetectionTest`:
   - Validates that SHA-256 hashes are deterministic.
   - Verifies that directory changes correctly identify `NEW`, `MODIFIED`, `DELETED`, and `UNCHANGED` files.
   - Verifies JSON manifest serialization and deserialization.
2. `EmbeddingCacheManagerTest`:
   - Validates binary disk writing and fast retrieval.
   - Tests hit rate calculation and miss tracking.
   - Verifies **cache isolation**: changing the model or preprocessing version results in a cache miss, ensuring safety.
3. `DL4JFeatureExtractorTest`:
   - Tests Deeplearning4j convolutional image loading (`NativeImageLoader`).
   - Verifies that intermediate activation layers produce the exact expected vector dimensions.

---

## 👥 How the Team Splits the Work

This repository provides the core foundation. Team members can take ownership of specific modules:

| Person | Focus Area | Key Classes to Use / Extend |
| :--- | :--- | :--- |
| **Person 1** | Architecture, Integration & Baseline | Coordinates repository, overall workflow, Full Recalculation baseline. |
| **Person 2** | Dataset & Fingerprinting | `FingerprintService`, `ManifestManager`, Caltech-101 dataset preparation (V1, V2, V3 scripts). |
| **Person 3** | Spark Core RDDs | `com.incremental.image.spark` — RDD partitioning, `mapPartitions` distributed execution. |
| **Person 4** | DL4J Feature Extraction | `DL4JFeatureExtractor` — model benchmarking (VGG16 vs ResNet), activation layer tuning. |
| **Person 5** | Cache & Benchmarking | `EmbeddingCacheManager`, `CacheStats`, execution time and throughput benchmark comparisons. |

---

## 📋 Experimental Goals to Compare
1. **Approach 1 — Full Recalculation:** Recomputes DL embeddings for all images on every run (no caching).
2. **Approach 2 — Timestamp Detection:** Skips images whose last modified timestamp hasn't changed.
3. **Approach 3 — Fingerprint-based Incremental (Our Core Approach):** Compares SHA-256 hashes and uses multi-version cache keys to skip redundant DL4J inference.
