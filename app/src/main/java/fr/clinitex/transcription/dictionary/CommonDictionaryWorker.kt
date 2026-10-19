package fr.clinitex.transcription.dictionary

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.clinitex.transcription.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Lot 6a: downloads the shared Clinitex word list when the phone is online,
 * at every app launch and every 12 hours. Offline, the app keeps using the
 * last list received.
 */
class CommonDictionaryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CommonDictionaryWorker"
        private const val PERIODIC_WORK = "common-dictionary-periodic"
        private const val NOW_WORK = "common-dictionary-now"

        private val online = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Every 12 h (kept if already scheduled) + one download as soon as there is network. Call at app launch. */
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CommonDictionaryWorker>(12, TimeUnit.HOURS)
                    .setConstraints(online)
                    .build(),
            )
            syncNow(context, ExistingWorkPolicy.KEEP)
        }

        /** "Mettre à jour" in Réglages › Dictionnaire: replaces any pending download. */
        fun syncNow(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                NOW_WORK,
                policy,
                OneTimeWorkRequestBuilder<CommonDictionaryWorker>().setConstraints(online).build(),
            )
        }
    }

    override suspend fun doWork(): Result {
        val client = CommonDictionaryClient(
            endpointUrl = BuildConfig.CLINITEX_DICTIONARY_URL,
            deviceToken = BuildConfig.CLINITEX_DEVICE_TOKEN,
        )
        if (!client.isConfigured()) return Result.success()
        return try {
            val received = client.fetch()
            CommonDictionaryRepository.save(applicationContext, received.copy(fetchedAt = System.currentTimeMillis()))
            Log.i(TAG, "common dictionary ${received.version}: ${received.words.size} words")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: CommonDictionaryClient.FetchException) {
            Log.w(TAG, "download failed: ${e.message}")
            if (e.retryable) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Log.w(TAG, "could not store the common dictionary: ${e.message}")
            Result.retry()
        }
    }
}

/**
 * Process-wide copy of the cached list: transcript post-processing reads it
 * for every segment, so the file is read once, then kept in memory.
 */
object CommonDictionaryRepository {
    private const val FILE_NAME = "common_dictionary.json"

    private val state = MutableStateFlow<CommonDictionary?>(null)
    @Volatile private var loaded = false

    private fun cache(context: Context) =
        CommonDictionaryCache(File(context.applicationContext.filesDir, FILE_NAME))

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (!loaded) {
                state.value = cache(context).read()
                loaded = true
            }
        }
    }

    /** Last list received; null before the first download. */
    fun current(context: Context): CommonDictionary? {
        ensureLoaded(context)
        return state.value
    }

    fun flow(context: Context): StateFlow<CommonDictionary?> {
        ensureLoaded(context)
        return state
    }

    fun save(context: Context, dictionary: CommonDictionary) {
        synchronized(this) {
            cache(context).write(dictionary)
            state.value = dictionary
            loaded = true
        }
    }
}
