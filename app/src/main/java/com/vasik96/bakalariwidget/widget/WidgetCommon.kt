package com.vasik96.bakalariwidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.glance.text.FontWeight
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vasik96.bakalariwidget.data.AuthException
import com.vasik96.bakalariwidget.data.Repo
import com.vasik96.bakalariwidget.data.nextBoundary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal fun ts(
    color: ColorProvider,
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    deco: TextDecoration? = null,
) = TextStyle(color = color, fontSize = size.sp, fontWeight = weight, textDecoration = deco)

/** Vrátí zcela čistý intent pro otevření hlavní domovské obrazovky oficiální aplikace Bakaláři (vynuluje data a extras, které způsobují GoException v Flutteru). */
fun openBakalariIntent(ctx: Context): Intent {
    val pm = ctx.packageManager
    val packages = listOf("cz.bakalari.mobile", "cz.bakalari.bakalari", "cz.bakalari.next")
    for (pkg in packages) {
        val launchIntent = pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            val comp = launchIntent.component
            if (comp != null) {
                return Intent.makeMainActivity(comp).apply {
                    data = null
                    replaceExtras(null as Bundle?)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
            }
            return launchIntent.apply {
                data = null
                replaceExtras(null as Bundle?)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
        }
    }
    return Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=cz.bakalari.mobile")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Glance akce pro otevření oficiální aplikace Bakaláři bez vkládání glance-action URI do intentu. */
class OpenBakalariAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = openBakalariIntent(context)
        context.startActivity(intent)
    }
}

/** Překreslí widgety z cache a naplánuje další překreslení na hranici hodiny. */
object WidgetUpdater {
    suspend fun updateAll(ctx: Context) {
        TimetableWidget().updateAll(ctx)
        MarksWidget().updateAll(ctx)
        Ticker.scheduleNext(ctx)
    }
}

object Ticker {
    suspend fun scheduleNext(ctx: Context) {
        val data = Repo(ctx).loadCache().data
        val now = LocalDateTime.now()
        val next = data?.let { nextBoundary(it, now) } ?: now.plusMinutes(30)
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, TickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi,
        )
    }
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { WidgetUpdater.updateAll(context.applicationContext) } finally { pending.finish() }
        }
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val r = runCatching { Repo(applicationContext).sync() }
        WidgetUpdater.updateAll(applicationContext)
        return when (r.exceptionOrNull()) {
            null -> Result.success()
            is AuthException -> Result.failure()
            else -> if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).setConstraints(net).build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun syncNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(net).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("sync-now", ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork("sync")
        }
    }
}
