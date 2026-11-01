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
import com.alushestore.data.local.dao.CorteDao
import com.alushestore.data.local.dao.InventarioDao
import com.alushestore.data.local.dao.MovimientoPendienteDao
import com.alushestore.data.local.dao.TianguisSesionDao
import com.alushestore.data.local.dao.TianguisVentaDao
import com.alushestore.data.local.dao.VentaDao
import com.alushestore.data.local.entities.AbonoEntity
import com.alushestore.data.local.entities.ApartadoEntity
import com.alushestore.data.local.entities.BaseEntity
import com.alushestore.data.local.entities.ClienteEntity
import com.alushestore.data.local.entities.CorteEntity
import com.alushestore.data.local.entities.CorteVendedorEntity
import com.alushestore.data.local.entities.DetalleVentaEntity
import com.alushestore.data.local.entities.LoteEntity
import com.alushestore.data.local.entities.MovimientoPendienteEntity
import com.alushestore.data.local.entities.ProductoEntity
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

// ── Migración 4 → 5: Lotes, Inventario, Punto de Venta (POS) y soporte Multiusuario ──
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Columnas multiusuario y POS en ventas
        db.execSQL("ALTER TABLE `ventas` ADD COLUMN `vendedor` TEXT NOT NULL DEFAULT 'PRINCIPAL'")
        db.execSQL("ALTER TABLE `ventas` ADD COLUMN `syncUuid` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `ventas` ADD COLUMN `esVentaPos` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ventas_syncUuid` ON `ventas` (`syncUuid`)")

        // 2. Columnas multiusuario y enlace a producto en apartados
        db.execSQL("ALTER TABLE `apartados` ADD COLUMN `vendedor` TEXT NOT NULL DEFAULT 'PRINCIPAL'")
        db.execSQL("ALTER TABLE `apartados` ADD COLUMN `syncUuid` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `apartados` ADD COLUMN `productoId` INTEGER DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_apartados_syncUuid` ON `apartados` (`syncUuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_apartados_productoId` ON `apartados` (`productoId`)")

        // 3. Tabla de Lotes de inversión
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `lotes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `syncUuid` TEXT NOT NULL,
                `nombre` TEXT NOT NULL,
                `fechaCompra` INTEGER NOT NULL,
                `inversionMercancia` REAL NOT NULL,
                `gastosExtraEnvio` REAL NOT NULL,
                `notas` TEXT,
                `activo` INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lotes_syncUuid` ON `lotes` (`syncUuid`)")

        // 4. Tabla de Productos de catálogo e inventario
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `productos` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `syncUuid` TEXT NOT NULL,
                `sku` TEXT NOT NULL,
                `nombre` TEXT NOT NULL,
                `categoria` TEXT,
                `loteId` INTEGER,
                `costoUnitario` REAL NOT NULL,
                `precioVenta` REAL NOT NULL,
                `stockDisponible` INTEGER NOT NULL,
                `stockApartado` INTEGER NOT NULL,
                `ubicacion` TEXT NOT NULL,
                `activo` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`loteId`) REFERENCES `lotes`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_productos_sku` ON `productos` (`sku`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_productos_syncUuid` ON `productos` (`syncUuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_productos_loteId` ON `productos` (`loteId`)")

        // 5. Tabla de Detalles de Venta POS (congelamiento de costo y utilidad)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `detalles_venta` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `ventaId` INTEGER NOT NULL,
                `productoId` INTEGER,
                `nombreProductoSnapshot` TEXT NOT NULL,
                `cantidad` INTEGER NOT NULL,
                `costoUnitarioHistorico` REAL NOT NULL,
                `precioUnitarioFinal` REAL NOT NULL,
                FOREIGN KEY(`ventaId`) REFERENCES `ventas`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`productoId`) REFERENCES `productos`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_detalles_venta_ventaId` ON `detalles_venta` (`ventaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_detalles_venta_productoId` ON `detalles_venta` (`productoId`)")
    }
}

// ── Migración 5 → 6: cortes de base (estado de cuenta por periodo) ────────────
// Solo agrega tablas nuevas: ninguna columna existente cambia, así que los datos
// de ventas, clientes e inventario que ya están en el teléfono quedan intactos.
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `cortes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `baseId` INTEGER NOT NULL,
                `syncUuid` TEXT NOT NULL,
                `nombrePeriodo` TEXT NOT NULL,
                `fechaInicio` INTEGER NOT NULL,
                `fechaCierre` INTEGER NOT NULL,
                `totalVendido` REAL NOT NULL,
                `numVentas` INTEGER NOT NULL,
                `ticketPromedio` REAL NOT NULL,
                `costoVendido` REAL NOT NULL,
                `utilidadNeta` REAL NOT NULL,
                `margenSobreCosto` INTEGER NOT NULL,
                `ventasConCosto` INTEGER NOT NULL,
                `ventasSinCosto` INTEGER NOT NULL,
                `valorInventarioCierre` REAL NOT NULL,
                `piezasInventarioCierre` INTEGER NOT NULL,
                `subidoEn` INTEGER,
                `creadoEn` INTEGER NOT NULL,
                FOREIGN KEY(`baseId`) REFERENCES `bases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cortes_baseId` ON `cortes` (`baseId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cortes_syncUuid` ON `cortes` (`syncUuid`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `corte_vendedores` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `corteId` INTEGER NOT NULL,
                `vendedor` TEXT NOT NULL,
                `totalVendido` REAL NOT NULL,
                `numVentas` INTEGER NOT NULL,
                FOREIGN KEY(`corteId`) REFERENCES `cortes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_corte_vendedores_corteId` ON `corte_vendedores` (`corteId`)")
    }
}

// ── Migración 6 → 7: cola de movimientos de stock sin mandar ─────────────────
// Sólo agrega una tabla nueva. Permite vender del almacén compartido sin señal:
// la venta se cobra igual y el descuento espera aquí a que vuelva el internet.
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `movimientos_pendientes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `productoRemotoId` INTEGER NOT NULL,
                `nombreProducto` TEXT NOT NULL,
                `tipo` TEXT NOT NULL,
                `cantidad` INTEGER NOT NULL,
                `motivo` TEXT NOT NULL,
                `vendedor` TEXT NOT NULL,
                `creadoEn` INTEGER NOT NULL,
                `intentos` INTEGER NOT NULL,
                `ultimoError` TEXT,
                `enConflicto` INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_movimientos_pendientes_productoRemotoId`
            ON `movimientos_pendientes` (`productoRemotoId`)
        """.trimIndent())
    }
}

/**
 * Apartar una pieza del almacén compartido.
 *
 * El apartado siempre vive aquí, porque es un compromiso con un cliente de este
 * teléfono, pero la pieza puede ser del catálogo web. `productoId` ya era
 * nullable; falta sólo a cuál producto de allá corresponde.
 *
 * Aditiva: no toca ni una fila existente.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `apartados` ADD COLUMN `productoRemotoId` INTEGER DEFAULT NULL")
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
        AbonoEntity::class,
        LoteEntity::class,
        ProductoEntity::class,
        DetalleVentaEntity::class,
        CorteEntity::class,
        CorteVendedorEntity::class,
        MovimientoPendienteEntity::class
    ],
    version = 8,
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
    abstract fun inventarioDao(): InventarioDao
    abstract fun corteDao(): CorteDao
    abstract fun movimientoPendienteDao(): MovimientoPendienteDao

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
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                )
                // SIN fallbackToDestructiveMigration, a propósito.
                //
                // Lo traía desde la 1.5.2 y es una trampilla, no una red: si una
                // migración truena, Room **borra la base y la recrea vacía**, sin
                // avisar. En el X7 y el M7 eso es perder el histórico de ventas.
                //
                // Sin esto, una migración rota lanza excepción y la app no abre.
                // Es feo, pero se arregla reinstalando y los datos siguen ahí.
                // Prefiero una app que no abre a una app que borra las cuentas.
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
