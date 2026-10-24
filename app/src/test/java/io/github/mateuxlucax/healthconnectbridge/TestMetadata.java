package io.github.mateuxlucax.healthconnectbridge;

import androidx.health.connect.client.records.metadata.DataOrigin;
import androidx.health.connect.client.records.metadata.Metadata;

import java.time.Instant;

/**
 * Builds record metadata as Health Connect returns it on read, i.e. with a data origin.
 * Metadata's full constructor is Kotlin-internal (public in bytecode), so it is reachable from Java only.
 */
final class TestMetadata {
    private TestMetadata() {}

    static Metadata from(String packageName) {
        return new Metadata(Metadata.RECORDING_METHOD_UNKNOWN, "id-" + packageName, new DataOrigin(packageName),
                Instant.EPOCH, null, 0L, null);
    }
}
