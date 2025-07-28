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

package com.kin.athena.data.remote

import com.kin.athena.core.logging.Logger
import com.kin.athena.core.utils.Error
import com.kin.athena.core.utils.Result
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import javax.inject.Inject
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

interface LicenseApi {
    @FormUrlEncoded
    @POST("api/licenses.php")
    suspend fun verifyLicense(
        @Field("action") action: String = "verify",
        @Field("key") key: String
    ): LicenseResponse
}


data class LicenseResponse(
    val success: Boolean,
    val error: String?,
    val valid: Boolean? = null,
    val license: LicenseData? = null
)

data class LicenseData(
    val type: String,
    val status: String,
    val expires_at: String?,
    val activations: ActivationData
)

data class ActivationData(
    val max: String,
    val current: Int
)


class LicenseRepositoryImpl @Inject constructor(
    private val api: LicenseApi
) : LicenseRepository {
    override suspend fun verifyLicense(key: String): LicenseResponse {
        return try {
            api.verifyLicense(key = key)
        } catch (e: IOException) {
            Logger.error("Network error verifying license: ${e.message}")
            LicenseResponse(
                success = false,
                error = "Network error: ${e.message}",
                valid = false,
                license = null
            )
        } catch (e: HttpException) {
            Logger.error("HTTP error verifying license: ${e.code()} - ${e.message}")
            LicenseResponse(
                success = false,
                error = "Server error: ${e.code()} - ${e.message}",
                valid = false,
                license = null
            )
        } catch (e: Exception) {
            Logger.error("Unexpected error verifying license: ${e.message}")
            LicenseResponse(
                success = false,
                error = "Unexpected error: ${e.message}",
                valid = false,
                license = null
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS) // Increase connection timeout
            .readTimeout(30, TimeUnit.SECONDS)    // Increase read timeout
            .writeTimeout(30, TimeUnit.SECONDS)   // Increase write timeout
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://admin.easyapps.me/") // Verify this URL is correct
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideLicenseRepository(api: LicenseApi): LicenseRepository {
        return LicenseRepositoryImpl(api)
    }

    @Provides
    @Singleton
    fun provideLicenseApi(retrofit: Retrofit): LicenseApi {
        return retrofit.create(LicenseApi::class.java)
    }

    @Provides
    @Singleton
    fun provideVerifyLicenseUseCase(repository: LicenseRepository): VerifyLicenseUseCase {
        return VerifyLicenseUseCase(repository)
    }
}

interface LicenseRepository {
    suspend fun verifyLicense(key: String): LicenseResponse
}

class VerifyLicenseUseCase @Inject constructor(
    private val repository: LicenseRepository
) {
    suspend operator fun invoke(key: String): Result<LicenseResponse, Error> {
        return try {
            Result.Success(repository.verifyLicense(key))
        } catch (e: Exception) {
            Result.Failure(Error.ServerError(e.message ?: "Error while verifying license"))
        }
    }
}