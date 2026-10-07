package com.cn.schrodinger.understatus.settings;

import java.util.Optional;
import java.util.Objects;

/** Large user-authored content is stored separately from small preferences. */
public interface ContentStore {
    /** Payload and recovery metadata from the same read, never a later operation. */
    record ReadResult(String value, String warning) {
        public ReadResult {
            Objects.requireNonNull(value);
            Objects.requireNonNull(warning);
        }
    }

    Optional<String> read(String key);
    /** Legacy stores without recovery metadata can continue implementing read. */
    default Optional<ReadResult> readResult(String key) {
        synchronized (this) {
            return read(key).map(value -> new ReadResult(value, warning()));
        }
    }
    void write(String key, String value);
    /** @deprecated Consume the warning on the immutable readResult instead. */
    @Deprecated
    default String warning() { return ""; }
}
