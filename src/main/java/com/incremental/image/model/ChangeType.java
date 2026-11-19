package com.incremental.image.model;

import java.io.Serializable;

/**
 * Categorization of file changes between collection snapshots.
 */
public enum ChangeType {
    NEW,
    MODIFIED,
    DELETED,
    UNCHANGED
}
