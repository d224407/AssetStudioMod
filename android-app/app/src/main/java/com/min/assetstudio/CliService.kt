package com.min.assetstudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Runs AssetStudioModCLI as a child process inside a foreground service. */
class CliService : Service() {

    companion object {
        const val ACTION_START = "com.min.assetstudio.START"
        const val ACTION_STOP = "com.min.assetstudio.STOP"
        private const val EXTRA_ARGS = "args"
        private const val CHANNEL = "cli"
        private const val NOTIF_ID = 1

        fun start(ctx: Context, args: Array<String>) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, CliService::class.java).setAction(ACTION_START).putExtra(EXTRA_ARGS, args)
            )
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, CliService::class.java).setAction(ACTION_STOP))
        }
    }

    @Volatile private var proc: Process? = null
    @Volatile private var stopping = false
    private var worker: Thread? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (worker?.isAlive == true) return START_NOT_STICKY
        val args = intent?.getStringArrayExtra(EXTRA_ARGS) ?: return START_NOT_STICKY

        createChannel()
        startForeground(NOTIF_ID, notification("Starting"))
        stopping = false
        CliState.clear()
        worker = Thread({ run(args) }, "cli").also { it.start() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopping = true
        proc?.let { p ->
            p.destroy()
            Thread {
                try {
                    if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly()
                } catch (_: InterruptedException) {
                }
            }.start()
        }
        wake?.let { if (it.isHeld) it.release() }
        if (CliState.phase == CliState.Phase.RUNNING || CliState.phase == CliState.Phase.INSTALLING) {
            CliState.log("Stopped")
            CliState.set(CliState.Phase.IDLE)
        }
        super.onDestroy()
    }

    private fun run(args: Array<String>) {
        try {
            if (!Build.SUPPORTED_ABIS.contains("arm64-v8a")) error("arm64-v8a device required")

            CliState.progress = 0
            CliState.set(CliState.Phase.INSTALLING)
            BinaryInstaller.install(this) { p ->
                CliState.progress = p
                notify("Installing $p%")
            }
            if (stopping) return

            val exe = BinaryInstaller.exe(this)
            val home = File(filesDir, "home").apply { mkdirs() }
            val tmp = File(cacheDir, "tmp").apply { mkdirs() }

            val pb = ProcessBuilder(listOf(exe.path) + args)
            pb.directory(home)
            pb.redirectErrorStream(true)
            pb.environment().apply {
                put("DOTNET_SYSTEM_GLOBALIZATION_INVARIANT", "1")
                put("DOTNET_gcServer", "0")
                put("DOTNET_TieredPGO", "0")
                put("HOME", home.path)
                put("TMPDIR", tmp.path)
                put("LD_LIBRARY_PATH", listOfNotNull(exe.parent, get("LD_LIBRARY_PATH")).joinToString(":"))
            }

            CliState.log("$ AssetStudioModCLI " + args.joinToString(" ") { if (it.contains(' ')) "\"$it\"" else it })
            val p = pb.start()
            proc = p
            (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "assetstudio:cli").also { wake = it; it.acquire() }
            CliState.set(CliState.Phase.RUNNING)
            notify("Running")

            try {
                p.inputStream.bufferedReader().forEachLine { CliState.log(it) }
            } catch (_: IOException) {
            }
            val code = p.waitFor()
            if (stopping) return
            CliState.log("Exit code $code")
            CliState.set(if (code == 0) CliState.Phase.DONE else CliState.Phase.ERROR)
        } catch (_: InterruptedException) {
        } catch (e: Exception) {
            CliState.log("ERROR: ${e.message ?: e.javaClass.simpleName}")
            CliState.set(CliState.Phase.ERROR)
        } finally {
            if (!stopping) stopSelf()
        }
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "AssetStudioMod", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(text: String): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, CliService::class.java).setAction(ACTION_STOP), flags
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("AssetStudioMod")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(R.drawable.ic_notif, "Stop", stop)
            .build()
    }

    private fun notify(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, notification(text))
    }
}
