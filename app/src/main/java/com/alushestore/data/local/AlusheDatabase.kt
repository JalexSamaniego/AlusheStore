package com.alushestore.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alushestore.data.local.dao.AbonoDao
import com.alushestore.data.local.dao.ApartadoDao
import com.alushestore.data.local.dao.BaseDao
import com.alushestore.data.local.dao.ClienteDao
import com.alushestore.data.local.dao.TianguisSesionDao
import com.alushestore.data.local.dao.TianguisVentaDao
import com.alushestore.data.local.dao.VentaDao
import com.alushestore.data.local.entities.AbonoEntity
import com.alushestore.data.local.entities.ApartadoEntity
import com.alushestore.data.local.entities.BaseEntity
import com.alushestore.data.local.entities.ClienteEntity
import com.alushestore.data.local.entities.TianguisSesionEntity
import com.alushestore.data.local.entities.TianguisVentaEntity
import com.alushestore.data.local.entities.VentaEntity

// ── Migración 1 → 2: agrega las tablas de Tianguis (no toca datos existentes) ──
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `tianguis_sesiones` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `nombre` TEXT NOT NULL,
                `fechaInicio` INTEGER NOT NULL,
                `fechaFin` INTEGER,
                `activa` INTEGER NOT NULL,
                `guardadaEnBase` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `tianguis_ventas` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sesionId` INTEGER NOT NULL,
                `descripcion` TEXT NOT NULL,
                `monto` REAL NOT NULL,
                `fecha` INTEGER NOT NULL,
                FOREIGN KEY(`sesionId`) REFERENCES `tianguis_sesiones`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_tianguis_ventas_sesionId`
            ON `tianguis_ventas` (`sesionId`)
        """.trimIndent())
    }
}

// ── Migración 2 → 3: agrega Clientes y Apartados (no toca datos existentes) ──
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `clientes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `nombre` TEXT NOT NULL,
                `telefono` TEXT NOT NULL,
                `notas` TEXT NOT NULL,
                `fechaCreacion` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `apartados` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `clienteId` INTEGER NOT NULL,
                `descripcion` TEXT NOT NULL,
                `monto` REAL NOT NULL,
                `anticipo` REAL NOT NULL,
                `estado` TEXT NOT NULL,
                `fecha` INTEGER NOT NULL,
                `notas` TEXT NOT NULL,
                `tipo` TEXT NOT NULL,
                FOREIGN KEY(`clienteId`) REFERENCES `clientes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_apartados_clienteId`
            ON `apartados` (`clienteId`)
        """.trimIndent())
    }
}

// ── Migración 3 → 4: agrega tabla de Abonos con trazabilidad y preserva anticipos previos ──
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `abonos` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `apartadoId` INTEGER NOT NULL,
                `baseId` INTEGER,
                `monto` REAL NOT NULL,
                `fecha` INTEGER NOT NULL,
                `nota` TEXT NOT NULL,
                FOREIGN KEY(`apartadoId`) REFERENCES `apartados`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_abonos_apartadoId`
            ON `abonos` (`apartadoId`)
        """.trimIndent())

        db.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_abonos_baseId`
            ON `abonos` (`baseId`)
        """.trimIndent())

        // Preservación de datos: inserta un abono por cada apartado existente con anticipo > 0
        db.execSQL("""
            INSERT INTO `abonos` (`apartadoId`, `baseId`, `monto`, `fecha`, `nota`)
            SELECT `id`, NULL, `anticipo`, `fecha`, 'Anticipo inicial'
            FROM `apartados`
            WHERE `anticipo` > 0
        """.trimIndent())
    }
}

@Database(
    entities = [
        BaseEntity::class,
        VentaEntity::class,
        TianguisSesionEntity::class,
        TianguisVentaEntity::class,
        ClienteEntity::class,
        ApartadoEntity::class,
        AbonoEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AlusheDatabase : RoomDatabase() {

    abstract fun baseDao(): BaseDao
    abstract fun ventaDao(): VentaDao
    abstract fun tianguisSesionDao(): TianguisSesionDao
    abstract fun tianguisVentaDao(): TianguisVentaDao
    abstract fun clienteDao(): ClienteDao
    abstract fun apartadoDao(): ApartadoDao
    abstract fun abonoDao(): AbonoDao

    /**
     * Vuelca transacciones de SQLite WAL (Write-Ahead Logging) al archivo principal.
     */
    fun checkpointWal() {
        try {
            openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AlusheDatabase? = null

        fun getDatabase(context: Context): AlusheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlusheDatabase::class.java,
                    "alushestore_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                // fallbackToDestructiveMigration solo como último recurso
                // en caso de versiones futuras sin migración definida
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
