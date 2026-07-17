package com.wavora.appdata.repository

import com.wavora.domain.model.model.update.ApkAsset
import com.wavora.domain.model.model.update.UpdateData
import com.wavora.domain.repository.UpdateRepository
import com.wavora.domain.utils.Resource
import com.wavora.scraper.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal class UpdateRepositoryImpl(
    private val youTube: YouTube,
) : UpdateRepository {
    override fun checkForGithubReleaseUpdate(): Flow<Resource<UpdateData>> =
        flow {
            youTube
                .checkForGithubReleaseUpdate()
                .onSuccess { response ->
                    val apkAssets =
                        response.assets
                            ?.filter { it?.name?.endsWith(".apk", ignoreCase = true) == true && it.browserDownloadUrl != null }
                            ?.map {
                                ApkAsset(
                                    name = it!!.name!!,
                                    downloadUrl = it.browserDownloadUrl!!,
                                    sizeBytes = it.size?.toLong(),
                                )
                            }
                            ?: emptyList()
                    val windowsZipAsset =
                        response.assets
                            ?.firstOrNull { it?.name?.equals("AppwavoraWindows.zip", ignoreCase = true) == true && it.browserDownloadUrl != null }
                    val windowsZipDownloadUrl = windowsZipAsset?.browserDownloadUrl
                    val windowsZipSha256 =
                        windowsZipAsset?.digest
                            ?.substringAfter("sha256:", missingDelimiterValue = "")
                            ?.takeIf { it.isNotBlank() }
                    emit(
                        Resource.Success(
                            UpdateData(
                                tagName = response.tagName ?: "",
                                releaseTime = response.publishedAt ?: "",
                                body = response.body ?: "",
                                // Naive fallback (first .apk found) - callers with
                                // access to the device's actual ABI (see
                                // AppUpdate.currentDeviceAbis()) should prefer
                                // picking from [apkAssets] instead of trusting this
                                // blindly, since asset order isn't guaranteed to
                                // put the right architecture first.
                                apkDownloadUrl = apkAssets.firstOrNull()?.downloadUrl,
                                apkAssets = apkAssets,
                                windowsZipDownloadUrl = windowsZipDownloadUrl,
                                windowsZipSha256 = windowsZipSha256,
                            ),
                        ),
                    )
                }.onFailure {
                    emit(Resource.Error<UpdateData>(it.localizedMessage ?: "Unknown error"))
                }
        }.flowOn(Dispatchers.IO)

    override fun checkForFdroidUpdate(): Flow<Resource<UpdateData>> =
        flow {
            youTube
                .checkForFdroidUpdate()
                .onSuccess { response ->
                    val latestVersion = response.packages.maxBy { it.versionCode }
                    emit(
                        Resource.Success(
                            UpdateData(
                                tagName = latestVersion.versionName,
                                releaseTime = null,
                                body =
                                    $$"""
                                    ### Update via F-Droid, changelogs: 
                                    - https://github.com/wavora-dev/Wavora/blob/main/fastlane/metadata/android/en-US/changelogs/$${latestVersion.versionCode}.txt
                                    """.trimIndent(),
                            ),
                        ),
                    )
                }.onFailure {
                    emit(Resource.Error<UpdateData>(it.localizedMessage ?: "Unknown error"))
                }
        }.flowOn(Dispatchers.IO)
}