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
package com.kin.athena.presentation.screens.settings.viewModel

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kin.athena.BuildConfig
import com.kin.athena.R
import com.kin.athena.core.logging.Logger
import com.kin.athena.data.remote.VerifyLicenseUseCase
import com.kin.athena.data.service.billing.BillingProvider
import com.kin.athena.domain.model.Settings
import com.kin.athena.domain.usecase.preferences.PreferencesUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.kin.athena.service.utils.manager.FirewallManager
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import java.security.NoSuchAlgorithmException


@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceUseCases: PreferencesUseCases,
    private val verifyLicenseUseCase: VerifyLicenseUseCase,
    private val billingProvider: BillingProvider,
    private val firewallManager: FirewallManager
) : ViewModel() {

    private val _settings = mutableStateOf(Settings())
    val settings: State<Settings> = _settings

    var defaultRoute: String? = null
    val version: String = BuildConfig.VERSION_NAME
    val build: String = BuildConfig.BUILD_TYPE

    init {
        runBlocking {
            loadSettings()
        }
    }

    fun verifyLicense(key: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            verifyLicenseUseCase.invoke(key).fold(
                ifSuccess = { licenseResponse ->
                    if (licenseResponse.valid == true) {
                        update(settings.value.copy(premiumUnlocked = true))
                        Toast.makeText(context, "✅ Premium activated successfully!", Toast.LENGTH_LONG).show()
                        onResult(true, "✅ Premium activated successfully!")
                    } else {
                        val errorMessage = licenseResponse.error ?: context.getString(R.string.invalid_license)
                        Toast.makeText(context, "❌ $errorMessage", Toast.LENGTH_LONG).show()
                        onResult(false, "❌ $errorMessage")
                    }
                },
                ifFailure = { error ->
                    val errorMessage = "❌ Failed to verify license: ${error.message}"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    onResult(false, errorMessage)
                }
            )
        }
    }

    fun startBilling(item: String, onSuccess: () -> Unit) {
        if (settings.value.premiumUnlocked) {
            onSuccess()
        } else {
            billingProvider.getBillingInterface()?.showPurchaseDialog(item, onSuccess)
                ?: Logger.error("BillingInterface not available - activity not set")
        }
    }

    fun loadDefaultRoute() {
        defaultRoute = _settings.value.defaultRoute
    }

    private suspend fun loadSettings() {
        val result = preferenceUseCases.loadSettings.execute()
        result.fold(
            ifSuccess = { loadedSettings ->
                _settings.value = loadedSettings
                defaultRoute = loadedSettings.defaultRoute
            }
        )
    }

    fun updateDefaultRoute(route: String) {
        _settings.value = _settings.value.copy(defaultRoute = route)
        viewModelScope.launch {
            preferenceUseCases.saveSettings.execute(_settings.value)
        }
    }

    fun update(newSettings: Settings, onSuccess: (() -> Unit)? = null) {
        val oldSettings = _settings.value
        _settings.value = newSettings.copy()
        viewModelScope.launch {
            try {
                preferenceUseCases.saveSettings.execute(newSettings).fold(
                    ifSuccess = {
                        // Check if blockPort80 setting changed
                        if (oldSettings.blockPort80 != newSettings.blockPort80) {
                            firewallManager.updateHttpSettings()
                        }
                        if (onSuccess != null) {
                            onSuccess()
                        }
                    },
                    ifFailure = { error ->
                        Logger.error("Failed to save settings: ${error.message}", error)
                    }
                )
            } catch (e: Exception) {
                Logger.error("Error updating settings: ${e.message}", e)
            }
        }
    }


    fun getAppSignature(): String? {
        return try {
            val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            val signatures = packageInfo.signatures

            if (signatures != null) {
                for (signature in signatures) {
                    val cert = signature.toByteArray()
                    val md = MessageDigest.getInstance("SHA-256")
                    val digest = md.digest(cert)
                    val hexString = digest.joinToString("") { "%02x".format(it) }
                    return hexString
                }
            }
        null
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            null
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            null
        }
    }

    fun getSupportedLanguages(context: Context): Map<String, String> {
        val localeList = getLocaleListFromXml(context)
        val map = mutableMapOf<String, String>()

        for (a in 0 until localeList.size()) {
            localeList[a].let {
                it?.let { it1 -> map.put(it1.getDisplayName(it), it.toLanguageTag()) }
            }
        }
        return map
    }

    // Taken from: https://stackoverflow.com/questions/74114067/get-list-of-locales-from-locale-config-in-android-13
    private fun getLocaleListFromXml(context: Context): LocaleListCompat {
        val tagsList = mutableListOf<CharSequence>()
        try {
            val xpp: XmlPullParser = context.resources.getXml(R.xml.locales_config)
            while (xpp.eventType != XmlPullParser.END_DOCUMENT) {
                if (xpp.eventType == XmlPullParser.START_TAG) {
                    if (xpp.name == "locale") {
                        tagsList.add(xpp.getAttributeValue(0))
                    }
                }
                xpp.next()
            }
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return LocaleListCompat.forLanguageTags(tagsList.joinToString(","))
    }
}