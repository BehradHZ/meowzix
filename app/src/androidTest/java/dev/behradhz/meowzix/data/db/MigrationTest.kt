package dev.behradhz.meowzix.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MeowzixDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrateOneToTwoAddsContentUriUniqueness() {
        helper.createDatabase(TEST_DATABASE_1_2, 1).close()
        helper.runMigrationsAndValidate(TEST_DATABASE_1_2, 2, true, MIGRATION_1_2).close()
    }

    @Test
    fun migrateTwoToThreeAddsTelegramSourceStorage() {
        helper.createDatabase(TEST_DATABASE_2_3, 2).close()
        helper.runMigrationsAndValidate(TEST_DATABASE_2_3, 3, true, MIGRATION_2_3).close()
    }

    @Test
    fun migrateThreeToFourAddsOfflineDownloadStorage() {
        helper.createDatabase(TEST_DATABASE_3_4, 3).close()
        helper.runMigrationsAndValidate(TEST_DATABASE_3_4, 4, true, MIGRATION_3_4).close()
    }

    @Test
    fun migrateFourToFiveAddsPlaylistStorage() {
        helper.createDatabase(TEST_DATABASE_4_5, 4).close()
        helper.runMigrationsAndValidate(TEST_DATABASE_4_5, 5, true, MIGRATION_4_5).close()
    }

    @Test
    fun migrateFiveToSixAddsListeningHistoryStorage() {
        helper.createDatabase(TEST_DATABASE_5_6, 5).close()
        helper.runMigrationsAndValidate(TEST_DATABASE_5_6, 6, true, MIGRATION_5_6).close()
    }

    @Test
    fun migrateSixToEightAddsCanonicalAudioFeatureStorage() {
        helper.createDatabase(TEST_DATABASE_6_8, 6).close()
        helper.runMigrationsAndValidate(
            TEST_DATABASE_6_8,
            8,
            true,
            MIGRATION_6_7,
            MIGRATION_7_8,
        ).close()
    }

    @Test
    fun migrateSevenToEightRepairsDerivedFeatureTableAndPreservesTracks() {
        val database = helper.createDatabase(TEST_DATABASE_7_8_REPAIR, 6)
        database.execSQL(
            "INSERT INTO `tracks` (`id`, `title`, `normalizedTitle`, `artist`, `normalizedArtist`, `album`, " +
                "`durationMs`, `trackNumber`, `year`, `artworkRef`, `favorite`, `hidden`, `createdAtEpochMs`, `updatedAtEpochMs`) " +
                "VALUES ('repair-track', 'Repair Track', 'repair track', NULL, NULL, NULL, 1000, NULL, NULL, NULL, 0, 0, 1, 1)",
        )
        MIGRATION_6_7.migrate(database)
        database.execSQL("DROP TABLE `audio_feature_vectors`")
        database.execSQL(
            "CREATE TABLE `audio_feature_vectors` (`id` TEXT NOT NULL, `legacyPayload` TEXT, PRIMARY KEY(`id`))",
        )
        database.version = 7
        database.close()

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE_7_8_REPAIR,
            8,
            true,
            MIGRATION_7_8,
        )
        migrated.query("SELECT `id` FROM `tracks` WHERE `id` = 'repair-track'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        migrated.close()
    }

    @Test
    fun migrateTenToElevenAddsTelegramSendQueue() {
        helper.createDatabase(TEST_DATABASE_10_11, 10).close()
        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE_10_11,
            11,
            true,
            MIGRATION_10_11,
        )
        migrated.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'telegram_send_jobs'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE_1_2 = "migration-test-1-2"
        const val TEST_DATABASE_2_3 = "migration-test-2-3"
        const val TEST_DATABASE_3_4 = "migration-test-3-4"
        const val TEST_DATABASE_4_5 = "migration-test-4-5"
        const val TEST_DATABASE_5_6 = "migration-test-5-6"
        const val TEST_DATABASE_6_8 = "migration-test-6-8"
        const val TEST_DATABASE_7_8_REPAIR = "migration-test-7-8-repair"
        const val TEST_DATABASE_10_11 = "migration-test-10-11"
    }
}
