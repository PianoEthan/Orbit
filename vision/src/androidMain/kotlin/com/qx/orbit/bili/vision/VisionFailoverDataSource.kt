package com.qx.orbit.bili.vision

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

@OptIn(UnstableApi::class)
internal class VisionFailoverDataSource(
    factory: DataSource.Factory,
    candidates: List<Uri>,
) : DataSource {
    private var spec: DataSpec? = null
    private val listeners = mutableListOf<TransferListener>()
    private val reader = VisionFailoverReader(
        candidateCount = candidates.size,
        create = { index ->
            IndexedSource(factory.createDataSource().also { source -> listeners.forEach(source::addTransferListener) }, candidates[index])
        },
        openSource = { source, offset -> source.upstream.open(checkNotNull(spec).subrange(offset).withUri(source.uri)) },
        readSource = { source, buffer, offset, length -> source.upstream.read(buffer, offset, length) },
        closeSource = { source -> source.upstream.close() },
    )

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        reader.activeSource?.upstream?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        close()
        spec = dataSpec
        return reader.open()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = reader.read(buffer, offset, length)
    override fun getUri(): Uri? = reader.activeSource?.upstream?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = reader.activeSource?.upstream?.responseHeaders.orEmpty()
    override fun close() {
        try { reader.close() } finally { spec = null }
    }

    private data class IndexedSource(val upstream: DataSource, val uri: Uri)
}
