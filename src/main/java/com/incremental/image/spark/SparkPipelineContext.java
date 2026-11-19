package com.incremental.image.spark;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;

import java.io.Closeable;
import java.io.Serializable;

/**
 * Manages the lifecycle and configuration of the Apache Spark context.
 * Strictly uses Spark Core RDDs (no Spark SQL/DataFrames).
 */
public class SparkPipelineContext implements Serializable, Closeable {
    private static final long serialVersionUID = 1L;

    private final String appName;
    private final String master;
    private final int defaultPartitions;

    private transient JavaSparkContext sparkContext;

    public SparkPipelineContext() {
        this("Incremental-DL-Feature-Embedding", "local[*]", Runtime.getRuntime().availableProcessors());
    }

    public SparkPipelineContext(String appName, String master, int defaultPartitions) {
        this.appName = appName;
        this.master = master;
        this.defaultPartitions = defaultPartitions;
    }

    /**
     * Get or initialize the active JavaSparkContext.
     */
    public synchronized JavaSparkContext getOrCreate() {
        if (sparkContext == null || sparkContext.sc().isStopped()) {
            SparkConf conf = new SparkConf()
                    .setAppName(appName)
                    .setMaster(master)
                    // Set serializer for fast distributed serialization
                    .set("spark.serializer", "org.apache.spark.serializer.JavaSerializer")
                    .set("spark.ui.enabled", "false") // Disable web UI for unit tests/clean runs
                    .set("spark.driver.bindAddress", "127.0.0.1")
                    .set("spark.driver.host", "127.0.0.1");

            this.sparkContext = new JavaSparkContext(conf);
        }
        return sparkContext;
    }

    public int getDefaultPartitions() {
        return defaultPartitions;
    }

    public String getAppName() {
        return appName;
    }

    public String getMaster() {
        return master;
    }

    @Override
    public synchronized void close() {
        if (sparkContext != null && !sparkContext.sc().isStopped()) {
            sparkContext.stop();
            sparkContext.close();
            sparkContext = null;
        }
    }
}
