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

package com.kin.athena.data.service.billing

import android.app.Activity
import com.kin.athena.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingProvider @Inject constructor() {
    
    private var currentActivity: Activity? = null
    private var currentBillingInterface: BillingInterface? = null
    
    fun setActivity(activity: Activity) {
        if (currentActivity != activity) {
            currentActivity = activity
            currentBillingInterface = createBillingInterface(activity)
        }
    }
    
    fun getBillingInterface(): BillingInterface? {
        return currentBillingInterface
    }
    
    private fun createBillingInterface(activity: Activity): BillingInterface {
        return if (BuildConfig.USE_PLAY_BILLING) {
            PlayStoreBillingManager(activity)
        } else {
            FDroidBillingManager(activity)
        }
    }
}