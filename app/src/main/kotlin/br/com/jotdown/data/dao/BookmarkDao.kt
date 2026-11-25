package br.com.jotdown.data.dao

import androidx.room.*
import br.com.jotdown.data.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards
abstract class BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY page ASC")
    abstract fun getBookmarksForDocument(documentId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId AND page = :page LIMIT 1")
    abstract suspend fun getBookmark(documentId: String, page: Int): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertBookmark(bookmark: BookmarkEntity): Long

    @Query("UPDATE bookmarks SET label = :label WHERE id = :id")
    abstract suspend fun updateLabel(id: Long, label: String): Int

    @Query("DELETE FROM bookmarks WHERE documentId = :documentId AND page = :page")
    abstract suspend fun deleteBookmark(documentId: String, page: Int): Int

    @Query("DELETE FROM bookmarks WHERE id = :id")
    abstract suspend fun deleteBookmarkById(id: Long): Int

    // Two-step unique-safe shift: negating shifted pages prevents UNIQUE(documentId, page) collisions.
    @Query("UPDATE bookmarks SET page = -(page + 1) WHERE documentId = :documentId AND page > :afterPage")
    abstract suspend fun negateShiftedPages(documentId: String, afterPage: Int): Int

    @Query("UPDATE bookmarks SET page = -page WHERE documentId = :documentId AND page < 0")
    abstract suspend fun restoreNegatedPages(documentId: String): Int
}
