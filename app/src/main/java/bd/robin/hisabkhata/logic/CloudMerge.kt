package bd.robin.hisabkhata.logic

enum class CloudChoice { KEEP_LOCAL, TAKE_REMOTE, TOMBSTONE }

/**
 * Newer [updatedAt] wins. A soft-delete wins when its deletedAt is at least as new
 * as the other side's update, so a delete is never dropped on the floor.
 */
fun cloudChoice(localUpdatedAt: Long?, remoteUpdatedAt: Long, remoteDeletedAt: Long): CloudChoice {
    if (remoteDeletedAt > 0L && (localUpdatedAt == null || remoteDeletedAt >= localUpdatedAt)) {
        return CloudChoice.TOMBSTONE
    }
    if (localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt) return CloudChoice.TAKE_REMOTE
    return CloudChoice.KEEP_LOCAL
}

data class RemoteDoc(val id: String, val updatedAt: Long, val deletedAt: Long = 0)

/** What a brand-new phone should show after applying a cloud snapshot. */
fun liveIdsAfterRestore(remote: List<RemoteDoc>): List<String> = remote.mapNotNull { doc ->
    if (cloudChoice(null, doc.updatedAt, doc.deletedAt) == CloudChoice.TAKE_REMOTE) doc.id else null
}
