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

package com.kin.athena.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kin.athena.core.utils.constants.AppConstants
import com.kin.athena.domain.model.Application

@Dao
interface ApplicationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(application: Application)

    @Update
    suspend fun update(application: Application)

    @Delete
    suspend fun delete(application: Application)

    @Query("SELECT * FROM applications")
    suspend fun getAllApplications(): List<Application>

    @Query("SELECT * FROM applications WHERE package_id = :packageId")
    suspend fun getApplicationByID(packageId: String): Application?

    @Query("SELECT EXISTS(SELECT 1 FROM applications WHERE package_id = :packageId)")
    suspend fun isPackageIdExists(packageId: String): Boolean
}