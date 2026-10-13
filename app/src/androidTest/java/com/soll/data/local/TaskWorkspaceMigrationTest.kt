package com.soll.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.soll.di.AppModule
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.Assert.assertEquals

class TaskWorkspaceMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val name = "task-workspace-migration-test"
    @get:Rule val helper = MigrationTestHelper(instrumentation, SollDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @After fun cleanTestDatabase() { instrumentation.targetContext.deleteDatabase(name) }

    @Test fun taskCacheUpgradePreservesRowsAndOtherTables() {
        helper.createDatabase(name, 26).apply {
            execSQL("INSERT INTO task_cache (id,title,description,source_ref,project_name,status,priority,due_date,tags_json,approval_id,tool_job_id,execution_state,outcome_artifacts_json,value_metric,branch,pair_id,assigned_node_id,required_capabilities_json,routing_state,execution_phase,execution_reason,updated_at) VALUES ('old-task','Saved task','','manual',NULL,'inbox','B',NULL,'[]',NULL,NULL,'','[]','','innovation',NULL,NULL,'[]','','','',1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 27, true, AppModule.migration26To27).apply {
            query("SELECT title, details_json FROM task_cache WHERE id='old-task'").use {
                assertEquals(true, it.moveToFirst())
                assertEquals("Saved task", it.getString(0))
                assertEquals("{}", it.getString(1))
            }
            close()
        }
    }
}
