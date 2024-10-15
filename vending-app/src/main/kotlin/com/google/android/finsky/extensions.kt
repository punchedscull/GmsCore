/*
 * SPDX-FileCopyrightText: 2023 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.finsky

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.collection.arrayMapOf
import androidx.collection.arraySetOf
import com.android.vending.licensing.AUTH_TOKEN_SCOPE
import com.android.vending.licensing.getAuthToken
import com.android.vending.licensing.getLicenseRequestHeaders
import com.google.android.finsky.assetmoduleservice.ModuleData
import com.google.android.finsky.assetmoduleservice.PackData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.microg.gms.auth.AuthConstants
import org.microg.vending.billing.core.HttpClient
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

const val STATUS_NOT_INSTALLED = 8
const val STATUS_COMPLETED = 4
const val STATUS_DOWNLOADING = 2
const val STATUS_INITIAL_STATE = 1

const val ERROR_CODE_SUCCESS = 0
const val ERROR_CODE_FAIL = -5

const val KEY_ERROR_CODE = "error_code"
const val KEY_MODULE_NAME = "module_name"
const val KEY_RESOURCE_PACKAGE_NAME = "resourcePackageName"

const val KEY_SESSION_ID = "session_id"
const val KEY_STATUS = "status"
const val KEY_PACK_VERSION = "pack_version"
const val KEY_PACK_BASE_VERSION = "pack_base_version"
const val KEY_BYTES_DOWNLOADED = "bytes_downloaded"
const val KEY_TOTAL_BYTES_TO_DOWNLOAD = "total_bytes_to_download"
const val KEY_PACK_NAMES = "pack_names"
const val KEY_APP_VERSION_CODE = "app_version_code"

const val KEY_CACHE_DIR = "CacheDir"
const val KEY_INDEX = "index"
const val KEY_CHUNK_NAME = "chunkName"
const val KEY_CHUNK_INTENT = "chunk_intents"
const val KEY_CHUNK_NUMBER = "chunk_number"
const val KEY_CHUNK_FILE_DESCRIPTOR = "chunk_file_descriptor"
const val KEY_RESOURCE_LINK = "resourceLink"
const val KEY_BYTE_LENGTH = "byteLength"
const val KEY_RESOURCE_BLOCK_NAME = "resourceBlockName"
const val KEY_UNCOMPRESSED_SIZE = "uncompressed_size"
const val KEY_UNCOMPRESSED_HASH_SHA256 = "uncompressed_hash_sha256"
const val KEY_NUMBER_OF_SUBCONTRACTORS = "numberOfSubcontractors"

const val KEY_USING_EXTRACTOR_STREAM = "usingExtractorStream"
const val KEY_COMPRESSION_FORMAT = "compression_format"
const val KEY_SLICE_IDS = "slice_ids"
const val KEY_SLICE_ID = "slice_id"
const val KEY_PLAY_CORE_VERSION_CODE = "playcore_version_code"

const val ACTION_VIEW = "android.intent.action.VIEW"
const val TAG_REQUEST = "asset_module"

private const val ACTION_SESSION_UPDATE = "com.google.android.play.core.assetpacks.receiver.ACTION_SESSION_UPDATE"
private const val EXTRA_SESSION_STATE = "com.google.android.play.core.assetpacks.receiver.EXTRA_SESSION_STATE"
private const val FLAGS = "com.google.android.play.core.FLAGS"

private const val ASSET_MODULE_DELIVERY_URL = "https://play-fe.googleapis.com/fdfe/assetModuleDelivery"

private const val TAG = "AssetModuleRequest"

fun combineModule(key: String, vararg moduleNames: String?): String {
    return moduleNames.joinToString(separator = ":", prefix = "$key:")
}

fun getAppVersionCode(context: Context, packageName: String): String? {
    return runCatching { context.packageManager.getPackageInfo(packageName, 0).versionCode.toString() }.getOrNull()
}

suspend fun HttpClient.initAssertModuleData(
    context: Context,
    packageName: String,
    accountManager: AccountManager,
    requestedAssetModuleNames: List<String?>,
    playCoreVersionCode: Int,
): ModuleData {
    Log.d(TAG, "initAssertModuleData: requestedAssetModuleNames: $requestedAssetModuleNames")
    val accounts = accountManager.getAccountsByType(AuthConstants.DEFAULT_ACCOUNT_TYPE)
    var oauthToken: String? = null
    if (accounts.isEmpty()) {
        return ModuleData(errorCode = ERROR_CODE_FAIL)
    } else for (account: Account in accounts) {
        oauthToken = accountManager.getAuthToken(account, AUTH_TOKEN_SCOPE, false).getString(AccountManager.KEY_AUTHTOKEN)
        if (oauthToken != null) {
            break
        }
    }
    if (oauthToken == null) {
        return ModuleData(errorCode = ERROR_CODE_FAIL)
    }
    val requestPayload = AssetModuleDeliveryRequest.Builder().callerInfo(CallerInfo(getAppVersionCode(context, packageName)?.toInt())).packageName(packageName)
        .playCoreVersion(playCoreVersionCode).pageSource(listOf(PageSource.UNKNOWN_SEARCH_TRAFFIC_SOURCE, PageSource.BOOKS_HOME_PAGE))
        .callerState(listOf(CallerState.CALLER_APP_REQUEST, CallerState.CALLER_APP_DEBUGGABLE)).moduleInfo(ArrayList<AssetModuleInfo>().apply {
            requestedAssetModuleNames.forEach { add(AssetModuleInfo.Builder().name(it).build()) }
        }).build()
    val moduleDeliveryInfo = runCatching {
        post(
            url = ASSET_MODULE_DELIVERY_URL,
            headers = getLicenseRequestHeaders(oauthToken, 1),
            payload = requestPayload,
            adapter = AssetModuleDeliveryResponse.ADAPTER
        ).wrapper?.deliveryInfo
    }.onFailure {
        Log.d(TAG, "initAssertModuleData: ", it)
    }.getOrNull()
    Log.d(TAG, "initAssertModuleData: moduleDeliveryInfo-> $moduleDeliveryInfo")
    return initModuleDownloadInfo(context, packageName, moduleDeliveryInfo)
}

suspend fun downloadFile(context: Context, moduleName: String, moduleData: ModuleData, bundle: Bundle) {
    val resourcePackageName: String? = bundle.getString(KEY_RESOURCE_PACKAGE_NAME)
    val chunkName: String? = bundle.getString(KEY_CHUNK_NAME)
    val resourceLink: String? = bundle.getString(KEY_RESOURCE_LINK)
    val byteLength: Long = bundle.getLong(KEY_BYTE_LENGTH)
    val index: Int = bundle.getInt(KEY_INDEX)
    val resourceBlockName: String? = bundle.getString(KEY_RESOURCE_BLOCK_NAME)
    if (resourcePackageName == null || chunkName == null || resourceLink == null || resourceBlockName == null) {
        Log.d(TAG, "downloadFile: params invalid  ")
        return
    }
    val filesDir = "${context.filesDir}/assetpacks/$index/$resourcePackageName/$chunkName/"
    val destination = File(filesDir, resourceBlockName)
    if (destination.exists() && destination.length() == byteLength) {
        sendBroadcastForExistingFile(context, moduleData, moduleName, bundle, destination)
        return
    }
    if (destination.exists()) {
        destination.delete()
    }
    val path = runCatching { download(context, resourceLink, destination, moduleName, moduleData) }.onFailure { Log.w(TAG, "downloadFile: ", it) }.getOrNull()
    if (path != null) {
        val file = File(path)
        if (file.exists() && file.length() == byteLength) {
            sendBroadcastForExistingFile(context, moduleData, moduleName, bundle, destination)
        }
    }
}

suspend fun download(
    context: Context,
    url: String,
    destinationFile: File,
    moduleName: String,
    moduleData: ModuleData,
): String = withContext(Dispatchers.IO) {
    val uri = Uri.parse(url).toString()
    val connection = URL(uri).openConnection() as HttpURLConnection
    var bytebit:Long = 0
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.connect()
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("Failed to download file: HTTP response code ${connection.responseCode}")
        }
        destinationFile.parentFile?.mkdirs()
        connection.inputStream.use { input ->
            FileOutputStream(destinationFile).use { output ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    moduleData.incrementPackBytesDownloaded(context, moduleName, bytesRead.toLong())
                    bytebit += bytesRead
                    if (bytebit >= 1048576){
                        sendBroadcastForExistingFile(context, moduleData, moduleName, null, null)
                        bytebit = 0
                    }
                }
            }
        }
        destinationFile.absolutePath
    } catch (e: Exception) {
        throw IOException("Download failed: ${e.message}", e)
    } finally {
        connection.disconnect()
    }
}

fun initModuleDownloadInfo(context: Context, packageName: String, deliveryInfo: ModuleDeliveryInfo?): ModuleData {
    if (deliveryInfo == null || deliveryInfo.status != null) {
        return ModuleData(errorCode = ERROR_CODE_FAIL)
    }
    val packNames: ArrayList<String> = arrayListOf()
    var moduleDownloadByteLength = 0L
    var appVersionCode = 0L
    val sessionIds = arrayMapOf<String, Int>()
    val packDataList = arrayMapOf<String, PackData>()
    for (deliveryIndex in deliveryInfo.res.indices) {
        val resource: ModuleResource = deliveryInfo.res[deliveryIndex]
        appVersionCode = resource.versionCode ?: 0
        val resourceList: List<PackResource> = resource.packResource
        val resourcePackageName: String = resource.packName ?: continue
        var packDownloadByteLength = 0L
        packNames.add(resourcePackageName)
        sessionIds[resourcePackageName] = deliveryIndex + 7
        var totalSumOfSubcontractedModules = 0
        val listOfSubcontractNames: ArrayList<String> = ArrayList()
        val bundlePackageName: ArrayList<Bundle> = arrayListOf()
        for (resIndex in resourceList.indices) {
            val packResource: PackResource = resourceList[resIndex]
            if (packResource.downloadInfo == null || packResource.chunkInfo == null) {
                continue
            }
            val downloadList = packResource.downloadInfo.download
            val numberOfSubcontractors = downloadList.size
            val uncompressedSize = packResource.downloadInfo.uncompressedSize
            val uncompressedHashSha256 = packResource.downloadInfo.uncompressedHashCode
            val chunkName = packResource.chunkInfo.chunkName?.also { listOfSubcontractNames.add(it) }
            var resDownloadByteLength = 0L
            for (downIndex in downloadList.indices) {
                val dResource: Download = downloadList[downIndex]
                resDownloadByteLength += dResource.byteLength!!
                totalSumOfSubcontractedModules += 1
                val bundle = Bundle()
                bundle.putString(KEY_CACHE_DIR, context.cacheDir.toString())
                bundle.putInt(KEY_INDEX, deliveryIndex + 7)
                bundle.putString(KEY_RESOURCE_PACKAGE_NAME, resourcePackageName)
                bundle.putString(KEY_CHUNK_NAME, chunkName)
                bundle.putString(KEY_RESOURCE_LINK, dResource.resourceLink)
                bundle.putLong(KEY_BYTE_LENGTH, dResource.byteLength)
                bundle.putString(KEY_RESOURCE_BLOCK_NAME, downIndex.toString())
                bundle.putLong(KEY_UNCOMPRESSED_SIZE, uncompressedSize ?: 0)
                bundle.putString(KEY_UNCOMPRESSED_HASH_SHA256, uncompressedHashSha256)
                bundle.putInt(KEY_NUMBER_OF_SUBCONTRACTORS, numberOfSubcontractors)
                bundlePackageName.add(bundle)
            }
            packDownloadByteLength += resDownloadByteLength
        }
        val packData = PackData(
            packVersion = appVersionCode,
            packBaseVersion = 0,
            sessionId = STATUS_NOT_INSTALLED,
            errorCode = ERROR_CODE_SUCCESS,
            status = STATUS_INITIAL_STATE,
            bytesDownloaded = 0,
            totalBytesToDownload = packDownloadByteLength,
            bundleList = bundlePackageName,
            totalSumOfSubcontractedModules = totalSumOfSubcontractedModules,
            listOfSubcontractNames = listOfSubcontractNames
        )
        moduleDownloadByteLength += packDownloadByteLength
        packDataList[resourcePackageName] = packData
    }
    return ModuleData(
        packageName = packageName,
        errorCode = ERROR_CODE_SUCCESS,
        sessionIds = sessionIds,
        bytesDownloaded = 0,
        status = STATUS_INITIAL_STATE,
        packNames = packNames,
        appVersionCode = appVersionCode,
        totalBytesToDownload = moduleDownloadByteLength
    ).apply {
        setPackData(packDataList)
    }
}

fun buildDownloadBundle(packName: String, moduleData: ModuleData, isPack: Boolean = false, packNames: List<String?>? = null) = Bundle().apply {
    val packData = moduleData.getPackData(packName)
    packData?.run {
        putInt(combineModule(KEY_SESSION_ID, packName), sessionId)
        putInt(combineModule(KEY_STATUS, packName), status)
        putInt(combineModule(KEY_ERROR_CODE, packName), errorCode)
        putLong(combineModule(KEY_PACK_VERSION, packName), packVersion)
        putLong(combineModule(KEY_PACK_BASE_VERSION, packName), packBaseVersion)
        putLong(combineModule(KEY_BYTES_DOWNLOADED, packName), bytesDownloaded)
        putLong(combineModule(KEY_TOTAL_BYTES_TO_DOWNLOAD, packName), totalBytesToDownload)

        putStringArrayList(KEY_PACK_NAMES, packNames?.let { ArrayList(it) } ?: if (isPack) arrayListOf(packName) else moduleData.packNames)
        putInt(KEY_STATUS, moduleData.status)
        putInt(KEY_APP_VERSION_CODE, moduleData.appVersionCode.toInt())
        putLong(KEY_TOTAL_BYTES_TO_DOWNLOAD, if (isPack) totalBytesToDownload else moduleData.totalBytesToDownload)
        putInt(KEY_ERROR_CODE, if (isPack) errorCode else moduleData.errorCode)
        putInt(KEY_SESSION_ID, moduleData.sessionIds?.get(packName) ?: sessionId)
        putLong(KEY_BYTES_DOWNLOADED, if (isPack) bytesDownloaded else moduleData.bytesDownloaded)
    }
}

fun sendBroadcastForExistingFile(context: Context, moduleData: ModuleData, moduleName: String, bundle: Bundle?, destination: File?) {
    val packData = moduleData.getPackData(moduleName) ?: return
    try {
        val downloadBundle = Bundle()
        downloadBundle.putInt(KEY_APP_VERSION_CODE, moduleData.appVersionCode.toInt())
        downloadBundle.putInt(KEY_ERROR_CODE, ERROR_CODE_SUCCESS)
        downloadBundle.putInt(KEY_SESSION_ID, moduleData.sessionIds?.get(moduleName) ?: moduleData.status)
        downloadBundle.putInt(KEY_STATUS, moduleData.status)
        downloadBundle.putStringArrayList(KEY_PACK_NAMES, arrayListOf(moduleName))
        downloadBundle.putLong(KEY_BYTES_DOWNLOADED, packData.bytesDownloaded)
        downloadBundle.putLong(KEY_TOTAL_BYTES_TO_DOWNLOAD, packData.totalBytesToDownload)
        downloadBundle.putLong(combineModule(KEY_TOTAL_BYTES_TO_DOWNLOAD, moduleName), packData.totalBytesToDownload)
        downloadBundle.putLong(combineModule(KEY_PACK_VERSION, moduleName), packData.packVersion)
        downloadBundle.putInt(combineModule(KEY_STATUS, moduleName), packData.status)
        downloadBundle.putInt(combineModule(KEY_ERROR_CODE, moduleName), ERROR_CODE_SUCCESS)
        downloadBundle.putLong(combineModule(KEY_BYTES_DOWNLOADED, moduleName), packData.bytesDownloaded)
        downloadBundle.putLong(combineModule(KEY_PACK_BASE_VERSION, moduleName), packData.packBaseVersion)
        val resultList = arraySetOf<Bundle>()
        packData.bundleList?.forEach {
            val result = Bundle()
            result.putString(KEY_CHUNK_NAME, it.getString(KEY_CHUNK_NAME))
            result.putLong(KEY_UNCOMPRESSED_SIZE, it.getLong(KEY_UNCOMPRESSED_SIZE))
            result.putString(KEY_UNCOMPRESSED_HASH_SHA256, it.getString(KEY_UNCOMPRESSED_HASH_SHA256))
            result.putInt(KEY_NUMBER_OF_SUBCONTRACTORS, it.getInt(KEY_NUMBER_OF_SUBCONTRACTORS))
            result.putLong(KEY_BYTE_LENGTH, it.getLong(KEY_BYTE_LENGTH))
            resultList.add(result)
        }
        resultList.forEach {
            val chunkName = it.getString(KEY_CHUNK_NAME)
            val uncompressedSize = it.getLong(KEY_UNCOMPRESSED_SIZE)
            val uncompressedHashSha256 = it.getString(KEY_UNCOMPRESSED_HASH_SHA256)
            val numberOfSubcontractors = it.getInt(KEY_NUMBER_OF_SUBCONTRACTORS)
            val chunkIntents: ArrayList<Intent?>
            if (destination == null) {
                chunkIntents = ArrayList(Collections.nCopies<Intent?>(numberOfSubcontractors, null))
            } else {
                val uFile = Uri.parse(destination.absolutePath).path?.let { path -> File(path) }
                chunkIntents = ArrayList(Collections.nCopies<Intent?>(numberOfSubcontractors, null))
                val uri = Uri.fromFile(uFile)
                context.grantUriPermission(moduleName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val intent = Intent(ACTION_VIEW)
                intent.setDataAndType(uri, context.contentResolver.getType(uri))
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val resourceBlockIndex = bundle?.getString(KEY_RESOURCE_BLOCK_NAME)?.toInt()
                if (uFile?.exists() == true && bundle?.getString(KEY_CHUNK_NAME) == chunkName && resourceBlockIndex != null) {
                    if (chunkIntents[resourceBlockIndex] == null) {
                        chunkIntents[resourceBlockIndex] = intent
                    }
                }
            }
            downloadBundle.putParcelableArrayList(combineModule(KEY_CHUNK_INTENT, moduleName, chunkName), chunkIntents)
            downloadBundle.putLong(combineModule(KEY_UNCOMPRESSED_SIZE, moduleName, chunkName), uncompressedSize)
            downloadBundle.putInt(combineModule(KEY_COMPRESSION_FORMAT, moduleName, chunkName), 1)
            downloadBundle.putString(combineModule(KEY_UNCOMPRESSED_HASH_SHA256, moduleName, chunkName), uncompressedHashSha256)
        }
        downloadBundle.putStringArrayList(combineModule(KEY_SLICE_IDS, moduleName), packData.listOfSubcontractNames)
        Log.d(TAG, "sendBroadcastForExistingFile: $downloadBundle")
        sendBroadCast(context, moduleData, downloadBundle)
    } catch (e: Exception) {
        Log.w(TAG, "sendBroadcastForExistingFile error:" + e.message)
    }
}

private fun sendBroadCast(context: Context, moduleData: ModuleData, result: Bundle) {
    val intent = Intent()
    intent.setAction(ACTION_SESSION_UPDATE)
    intent.putExtra(EXTRA_SESSION_STATE, result)
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
    intent.putExtra(FLAGS, Bundle().apply { putBoolean(KEY_USING_EXTRACTOR_STREAM, true) })
    intent.setPackage(moduleData.packageName)
    context.sendBroadcast(intent)
}
