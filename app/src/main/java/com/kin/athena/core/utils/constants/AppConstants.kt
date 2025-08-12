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

package com.kin.athena.core.utils.constants


object AppConstants {
    object AnimationConstants {
        const val FADE_DURATION: Int = 300
        const val SCALE_DURATION: Int = 400
        const val SLIDE_DURATION: Int = 400
        const val INITIAL_SCALE: Float = 0.9f
    }

    object DatabaseConstants {
        const val DATABASE_NAME: String = "packages"
        const val DATABASE_VERSION: Int = 3
        const val SETTINGS_DATABASE_NAME: String = "settings"
    }

    object NetworkConstants {
        const val NFLOG_GROUP_ID: Int = 58
    }

    object DnsBlockLists {
        val MALWARE_PROTECTION = "https://raw.githubusercontent.com/DandelionSprout/adfilt/master/Alternate%20versions%20Anti-Malware%20List/AntiMalwareHosts.txt"
        val AD_PROTECTION = "https://adaway.org/hosts.txt"
        val PRIVACY_PROTECTION = "https://hostfiles.frogeye.fr/firstparty-trackers-hosts.txt"
        val GAMBLING_PROTECTION = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/gambling-only/hosts"
        val ADULT_PROTECTION = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts"
        val SOCIAL_PROTECTION = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/social-only/hosts"
    }
}