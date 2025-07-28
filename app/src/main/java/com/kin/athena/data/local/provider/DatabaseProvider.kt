/*
 * Copyright (C) 2025 Vexzure
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package com.kin.athena.data.local.provider

import android.app.Application
import androidx.room.Room
import com.kin.athena.core.utils.constants.AppConstants
import com.kin.athena.data.local.dao.ApplicationDao
import com.kin.athena.data.local.dao.LogDao
import com.kin.athena.data.local.dao.NetworkFilterDao
import com.kin.athena.data.local.database.AppDatabase

class DatabaseProvider(private val application: Application) {

    @Volatile
    private var database: AppDatabase? = null

    @Synchronized
    fun instance(): AppDatabase {
        return database ?: synchronized(this) {
            database ?: buildDatabase().also { database = it }
        }
    }

    private fun buildDatabase(): AppDatabase {
        return Room.databaseBuilder(application.applicationContext,
            AppDatabase::class.java,
            AppConstants.DatabaseConstants.DATABASE_NAME)
            .build()
    }

    @Synchronized
    fun close() {
        database?.close()
        database = null
    }

    fun applicationDao(): ApplicationDao {
        return instance().packageDao()
    }

    fun logDao(): LogDao {
        return instance().logDao()
    }

    fun networkFilterDao(): NetworkFilterDao {
        return instance().blockedDao()
    }
}
