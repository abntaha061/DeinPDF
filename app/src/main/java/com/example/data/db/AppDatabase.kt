package com.example.data.db

import android.content.Context
import androidx.room.*
import com.example.data.model.*
import com.example.data.model.Annotation
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfFileDao {
    @Query("SELECT * FROM pdf_files ORDER BY lastOpened DESC")
    fun getAllFiles(): Flow<List<PdfFile>>

    @Query("SELECT * FROM pdf_files WHERE isFavorite = 1 ORDER BY lastOpened DESC")
    fun getFavorites(): Flow<List<PdfFile>>

    @Query("SELECT * FROM pdf_files WHERE category = :cat ORDER BY lastOpened DESC")
    fun getByCategory(cat: String): Flow<List<PdfFile>>

    @Query("SELECT * FROM pdf_files WHERE name LIKE '%' || :query || '%' ORDER BY lastOpened DESC")
    fun search(query: String): Flow<List<PdfFile>>

    @Query("SELECT * FROM pdf_files WHERE id = :id")
    suspend fun getById(id: Long): PdfFile?

    @Query("SELECT * FROM pdf_files WHERE path = :path OR uri = :path LIMIT 1")
    suspend fun getByPath(path: String): PdfFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: PdfFile): Long

    @Update
    suspend fun update(file: PdfFile)

    @Delete
    suspend fun delete(file: PdfFile)

    @Query("UPDATE pdf_files SET lastOpened = :time, lastPage = :page, readProgress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, page: Int, progress: Float, time: Long = System.currentTimeMillis())

    @Query("UPDATE pdf_files SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("SELECT * FROM pdf_files ORDER BY lastOpened DESC LIMIT 10")
    fun getRecent(): Flow<List<PdfFile>>

    @Query("SELECT * FROM pdf_files ORDER BY name ASC")
    fun getAllSortedByName(): Flow<List<PdfFile>>

    @Query("SELECT * FROM pdf_files ORDER BY size DESC")
    fun getAllSortedBySize(): Flow<List<PdfFile>>
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE pdfId = :pdfId ORDER BY page ASC")
    fun getForPdf(pdfId: Long): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark): Long

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE pdfId = :pdfId")
    suspend fun deleteForPdf(pdfId: Long)
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE pdfId = :pdfId AND page = :page")
    fun getForPage(pdfId: Long, page: Int): Flow<List<Annotation>>

    @Query("SELECT * FROM annotations WHERE pdfId = :pdfId")
    fun getForPdf(pdfId: Long): Flow<List<Annotation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(annotation: Annotation): Long

    @Delete
    suspend fun delete(annotation: Annotation)

    @Query("SELECT COUNT(*) FROM annotations WHERE pdfId = :pdfId")
    suspend fun countForPdf(pdfId: Long): Int
}

@Dao
interface VocabularyDao {
    @Query("SELECT * FROM vocabulary ORDER BY addedAt DESC")
    fun getAll(): Flow<List<VocabularyWord>>

    @Query("SELECT * FROM vocabulary ORDER BY addedAt DESC")
    suspend fun getAllWordsOnce(): List<VocabularyWord>

    @Query("SELECT * FROM vocabulary WHERE nextReviewAt <= :now AND isMastered = 0 ORDER BY nextReviewAt ASC LIMIT 20")
    fun getDueForReview(now: Long = System.currentTimeMillis()): Flow<List<VocabularyWord>>

    @Query("SELECT * FROM vocabulary WHERE originalWord LIKE '%' || :q || '%' OR translatedWord LIKE '%' || :q || '%'")
    fun search(q: String): Flow<List<VocabularyWord>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: VocabularyWord): Long

    @Update
    suspend fun update(word: VocabularyWord)

    @Delete
    suspend fun delete(word: VocabularyWord)

    @Query("SELECT COUNT(*) FROM vocabulary")
    suspend fun count(): Int
}

@Dao
interface ReadHistoryDao {
    @Query("SELECT * FROM read_history ORDER BY openedAt DESC LIMIT 100")
    fun getAll(): Flow<List<ReadHistory>>

    @Query("SELECT SUM(durationMs) FROM read_history WHERE openedAt >= :since")
    suspend fun totalTimeMs(since: Long): Long?

    @Query("SELECT SUM(pagesRead) FROM read_history WHERE openedAt >= :since")
    suspend fun totalPages(since: Long): Int?

    @Insert
    suspend fun insert(history: ReadHistory)

    @Query("DELETE FROM read_history WHERE openedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

class Converters {
    @TypeConverter
    fun fromAnnotationType(value: AnnotationType): String = value.name

    @TypeConverter
    fun toAnnotationType(value: String): AnnotationType = AnnotationType.valueOf(value)
}

@Database(
    entities = [PdfFile::class, Bookmark::class, Annotation::class, VocabularyWord::class, ReadHistory::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfFileDao(): PdfFileDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun readHistoryDao(): ReadHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pdf_reader_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
