package com.example.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from Database v1 to v2:
 * Creates `command_snippets` and `clipboard_items` tables.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `command_snippets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `command` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `isFavorite` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `clipboard_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `hostId` INTEGER NOT NULL,
                `content` TEXT NOT NULL,
                `direction` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * Migration from Database v2 to v3:
 * Expands `hosts` table to support Tailscale mesh networking,
 * dual-path fallback (LAN), Arcade SRE ports, and SecureVault alias tracking.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `hosts` ADD COLUMN `tailscaleAddress` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `hosts` ADD COLUMN `lanAddress` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `hosts` ADD COLUMN `connectionMode` TEXT NOT NULL DEFAULT 'AUTO'")
        db.execSQL("ALTER TABLE `hosts` ADD COLUMN `activeEndpoint` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `hosts` ADD COLUMN `lastLatencyMs` INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE `hosts` ADD COLUMN `arcadePort` INTEGER NOT NULL DEFAULT 8899")
        db.execSQL("ALTER TABLE `hosts` ADD COLUMN `eveboxPort` INTEGER NOT NULL DEFAULT 5636")
        db.execSQL("ALTER TABLE `hosts` ADD COLUMN `vaultAlias` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `hosts` ADD COLUMN `apiKey` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `hosts` ADD COLUMN `connectionState` TEXT NOT NULL DEFAULT 'DISCONNECTED'")
    }
}
