package dev.behradhz.meowzix.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    private companion object {
        const val TEST_DATABASE_1_2 = "migration-test-1-2"
        const val TEST_DATABASE_2_3 = "migration-test-2-3"
    }
}
