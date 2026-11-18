package bd.robin.hisabkhata.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PartyEntity::class,
        EntryEntity::class,
        CashEntity::class,
        ProductEntity::class,
        SaleEntity::class,
        DeletionEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class HisabDatabase : RoomDatabase() {
    abstract fun parties(): PartyDao
    abstract fun entries(): EntryDao
    abstract fun cash(): CashDao
    abstract fun products(): ProductDao
    abstract fun sales(): SaleDao
    abstract fun deletions(): DeletionDao

    companion object {
        fun create(context: Context): HisabDatabase =
            Room.databaseBuilder(context, HisabDatabase::class.java, "hisab.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("parties", "entries", "cash", "products", "sales", "deletions").forEach { table ->
                    db.execSQL("ALTER TABLE $table ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("parties", "entries", "cash", "products", "sales", "deletions").forEach { table ->
                    db.execSQL("ALTER TABLE $table ADD COLUMN businessId TEXT NOT NULL DEFAULT ''")
                }
            }
        }
    }
}

@Database(
    entities = [SensitiveEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun sensitive(): SensitiveDao

    companion object {
        fun create(context: Context): VaultDatabase =
            Room.databaseBuilder(context, VaultDatabase::class.java, "vault.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
