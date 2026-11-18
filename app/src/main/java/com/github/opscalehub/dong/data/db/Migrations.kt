package com.github.opscalehub.dong.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Every schema change gets an explicit, tested migration (see MigrationTest). Never destructive. */
object Migrations {
    /** v2: payment method on journal entries; index for per-unit statement queries. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE journal_entries ADD COLUMN method TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_lines_unitId ON journal_lines(unitId)")
        }
    }

    /** v3: display currency (e.g. show Toman while storing Rial). Existing buildings keep their display. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE buildings ADD COLUMN displayCurrencyName TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE buildings ADD COLUMN displayDivisor INTEGER NOT NULL DEFAULT 1")
        }
    }

    /** v4: which units' bills were sent for a period (Home step «ارسال قبض واحدها»). */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bill_shares` (`id` TEXT NOT NULL, `buildingId` TEXT NOT NULL, `periodId` TEXT NOT NULL, " +
                    "`unitId` TEXT NOT NULL, `at` INTEGER NOT NULL, `via` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`buildingId`) REFERENCES `buildings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`periodId`) REFERENCES `periods`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`unitId`) REFERENCES `units`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_shares_buildingId` ON `bill_shares` (`buildingId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_shares_periodId` ON `bill_shares` (`periodId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_shares_unitId` ON `bill_shares` (`unitId`)")
        }
    }

    /**
     * v5: charge items paid in advance (the monthly charge is due by the 10th of the month itself).
     * Buildings set up with a fixed monthly charge (the wizard's «شارژ ماهانه» type) switch to it.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE charge_items ADD COLUMN inAdvance INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "UPDATE charge_items SET inAdvance = 1 WHERE mode != 'WATER_TARIFF' AND categoryId IN " +
                    "(SELECT id FROM categories WHERE name = 'شارژ ماهانه' AND active = 0)",
            )
        }
    }

    /** v6: where residents pay (card/IBAN and holder), printed on bills and reminders. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE buildings ADD COLUMN payAccount TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE buildings ADD COLUMN payHolder TEXT NOT NULL DEFAULT ''")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
