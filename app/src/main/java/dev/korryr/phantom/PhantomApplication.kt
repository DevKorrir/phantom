package dev.korryr.phantom

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.InetAddress

@HiltAndroidApp
class PhantomApplication : Application() {

    // App-level coroutine scope — survives config changes, dies with process
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        Timber.d("PhantomApplication initialized")

        // DNS pre-resolve: warm the system DNS cache so the first Groq API call
        // doesn't pay the 50-200ms DNS lookup penalty.
        appScope.launch {
            try {
                val addresses = InetAddress.getAllByName("api.groq.com")
                Timber.d("DNS pre-resolved api.groq.com → ${addresses.map { it.hostAddress }}")
            } catch (e: Exception) {
                Timber.w(e, "DNS pre-resolve failed (non-fatal)")
            }
        }
    }
}
