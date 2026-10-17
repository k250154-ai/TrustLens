package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.ExtractedClaim
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Entity(tableName = "claims_cache")
data class ClaimCacheEntity(
    @PrimaryKey val claimHash: String,
    val claimText: String,
    val verdict: String,
    val confidence: Float,
    val serializedClaimJson: String,
    val createdAtTimestamp: Long,
    val lastRevalidatedTimestamp: Long,
    val hitsCount: Int = 1,
    val sourceContext: String? = null
)

@Dao
interface ClaimCacheDao {
    @Query("SELECT * FROM claims_cache WHERE claimHash = :hash LIMIT 1")
    suspend fun getClaimByHash(hash: String): ClaimCacheEntity?

    @Query("SELECT * FROM claims_cache ORDER BY lastRevalidatedTimestamp DESC LIMIT :limit")
    suspend fun getRecentVerifiedClaims(limit: Int = 50): List<ClaimCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(claim: ClaimCacheEntity)

    @Query("UPDATE claims_cache SET hitsCount = hitsCount + 1, lastRevalidatedTimestamp = :now WHERE claimHash = :hash")
    suspend fun recordHit(hash: String, now: Long)

    @Query("DELETE FROM claims_cache WHERE createdAtTimestamp < :threshold")
    suspend fun purgeOlderThan(threshold: Long)

    @Query("DELETE FROM claims_cache")
    suspend fun clearAll()
}

class Converters {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(ExtractedClaim::class.java)

    @TypeConverter
    fun fromClaim(claim: ExtractedClaim): String {
        return adapter.toJson(claim)
    }

    @TypeConverter
    fun toClaim(json: String): ExtractedClaim? {
        return runCatching { adapter.fromJson(json) }.getOrNull()
    }
}

@Database(entities = [ClaimCacheEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class TrustLensDatabase : RoomDatabase() {
    abstract fun claimCacheDao(): ClaimCacheDao

    companion object {
        @Volatile
        private var instance: TrustLensDatabase? = null

        fun getDatabase(context: android.content.Context): TrustLensDatabase {
            return instance ?: synchronized(this) {
                instance ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    TrustLensDatabase::class.java,
                    "trustlens_db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
            }
        }
    }
}
