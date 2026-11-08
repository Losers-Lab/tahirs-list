package com.tahirslist.ingestion.artifacts

import java.nio.file.Path

/**
 * The out-port `RunSampleIngestion` and `CompileManualBatch` write through.
 *
 * It is deliberately narrow: a run may start a directory and complete it once;
 * it can never write into another run (D-17). `markLoaded`/`archive` belong to
 * the load/console side and stay on [FileRunArtifacts].
 */
interface ArtifactStore {
    fun begin(runId: String, onPending: PendingRunPolicy = PendingRunPolicy.ABORT): Path

    fun complete(
        runId: String,
        candidatesJson: String,
        reportMarkdown: String,
        snapshots: Map<String, ByteArray> = emptyMap(),
    ): PendingRun
}
