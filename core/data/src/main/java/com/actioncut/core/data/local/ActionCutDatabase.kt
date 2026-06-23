package com.actioncut.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.actioncut.core.data.local.dao.ProjectDao
import com.actioncut.core.data.local.entity.ProjectEntity

/** Room database for ActionCut project metadata. */
@Database(
    entities = [ProjectEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ActionCutDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        const val NAME = "actioncut.db"
    }
}
