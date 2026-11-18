package bd.robin.hisabkhata.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PartyEntity::class,
        EntryEntity::class,
        CashEntity::class,
        ProductEntity::class,
        SaleEntity::class,
        DeletionEntity::class,
    ],
    version = 1,
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
                .fallbackToDestructiveMigration()
                .build()
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
