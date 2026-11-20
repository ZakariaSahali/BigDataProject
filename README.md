# Incremental DL Feature Embedding for Repeated Image Collections

> **Big Data University Project**  
> **Technologies:** Java 11, Apache Spark Core (JavaRDDs), Deeplearning4j (DL4J - VGG16), Maven, Git.

---

## 📌 Project Overview (For Beginners)

When working with large collections of images (such as Caltech-101) in real-world scenarios, datasets change over time:
- New images are added.
- Existing images are updated or replaced.
- Outdated images are removed.
- **Most images remain completely unchanged.**

Extracting deep learning feature vectors (embeddings) using neural networks like **VGG16** is computationally expensive. Running feature extraction on the entire collection every time a change happens wastes massive amounts of CPU/GPU time, memory, and power.

### Our Solution
This project builds an **Incremental Feature Extraction Pipeline**:
1. **Snapshots & Fingerprints:** Calculates SHA-256 cryptographic fingerprints for every image and saves them in a lightweight JSON manifest.
2. **Change Detection:** Automatically compares the new dataset version against the previous version to find what actually changed (`NEW`, `MODIFIED`, `DELETED`, `UNCHANGED`).
3. **Smart Persistent Caching:** Reuses previously computed embeddings for unchanged images, skipping expensive neural network inference.
4. **Distributed Processing:** Uses **Apache Spark Core RDDs** to distribute the extraction across worker cores using partition-level resource initialization (`mapPartitions`).
5. **Strict Cache Isolation:** Embeddings are cached using a composite key: `(image_sha256, model_version, preprocessing_version)`. If you switch models or change image resizing rules, incompatible cached embeddings will never be mistakenly reused.

---

## 🏗️ Architecture & Completed Components

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
│   └── DL4JFeatureExtractor.java  # Preprocessing + Deeplearning4j VGG16 intermediate layer extraction
│
├── spark/           # Apache Spark Core RDD Distributed Pipeline
│   ├── SparkPipelineContext.java  # Driver configuration, JavaSparkContext lifecycle management
│   └── SparkEmbeddingPipeline.java# Partition-level distributed caching & inference via mapPartitions
│
└── benchmark/       # Benchmark suite for comparing Approaches 1, 2, and 3
```

---

## 🧠 Deep Learning Model Setup (VGG16)

Deeplearning4j uses pre-trained weights for feature extraction. The pipeline extracts 4096-dimensional embeddings from the intermediate `fc2` layer of VGG16.

### Model Location:
Pretrained weights archive (~490 MB):
```bash
~/.deeplearning4j/models/vgg16/vgg16_dl4j_inference.zip
```

> [!NOTE]  
> The model archive is loaded directly into DL4J via `ModelSerializer.restoreComputationGraph` to ensure fast initialization and avoid multi-worker download race conditions on Spark clusters.

---

## 🚀 How to Run and Test

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

### Build & Run Unit Tests
To compile and execute unit tests (excluding heavy neural net downloads):
```bash
mvn test -Dtest=FingerprintAndChangeDetectionTest,EmbeddingCacheManagerTest,DL4JFeatureExtractorTest
```

### Run Spark Distributed Pipeline Test
```bash
mvn test -Dtest=SparkEmbeddingPipelineTest
```

---

## 🧪 What the Automated Tests Verify:

1. `FingerprintAndChangeDetectionTest`:
   - Validates that SHA-256 hashes are deterministic across files.
   - Verifies that directory changes correctly identify `NEW`, `MODIFIED`, `DELETED`, and `UNCHANGED` files.
   - Verifies JSON manifest serialization and deserialization.
2. `EmbeddingCacheManagerTest`:
   - Validates binary disk writing and fast retrieval of float arrays.
   - Tests hit rate calculation and miss tracking.
   - Verifies **cache isolation**: changing model or preprocessing version forces a cache miss.
3. `DL4JFeatureExtractorTest`:
   - Tests Deeplearning4j convolutional image loading (`NativeImageLoader`).
   - Verifies intermediate activation layers produce the exact expected vector dimensions.
4. `SparkEmbeddingPipelineTest`:
   - Runs full distributed feature extraction on Apache Spark Core `JavaRDD`s.
   - Confirms that V1 run populates cache (4 misses) and identical V2 re-run executes with 100% cache hits and zero Spark inference jobs.

---

## 👥 How the Team Splits the Work

| Person | Focus Area | Key Classes to Use / Extend |
| :--- | :--- | :--- |
| **Person 1** | Architecture, Integration & Baseline | Coordinates repository, overall workflow, Full Recalculation baseline. |
| **Person 2** | Dataset & Fingerprinting | `FingerprintService`, `ManifestManager`, Caltech-101 dataset preparation (V1, V2, V3 scripts). |
| **Person 3** | Spark Core RDDs | `SparkPipelineContext`, `SparkEmbeddingPipeline` — RDD partitioning, `mapPartitions` distributed execution. |
| **Person 4** | DL4J Feature Extraction | `DL4JFeatureExtractor` — VGG16 model benchmarking, activation layer tuning. |
| **Person 5** | Cache & Benchmarking | `EmbeddingCacheManager`, `CacheStats`, execution time and throughput benchmark comparisons. |

---

## 📋 Experimental Approaches to Compare
1. **Approach 1 — Full Recalculation:** Recomputes DL embeddings for all images on every run (no caching).
2. **Approach 2 — Timestamp Detection:** Skips images whose last modified timestamp hasn't changed.
3. **Approach 3 — Fingerprint-based Incremental (Our Core Approach):** Compares SHA-256 hashes and uses multi-version cache keys to skip redundant DL4J inference.
