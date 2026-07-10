package com.example.amexbenefittracker.worker

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.amexbenefittracker.AmexApplication
import com.example.amexbenefittracker.data.local.entities.Benefit
import com.example.amexbenefittracker.data.local.entities.BenefitType
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object BenefitNotificationHelper {
    fun isLastMonthOfPeriod(type: BenefitType, month0Indexed: Int): Boolean {
        return when (type) {
            BenefitType.MONTHLY -> true
            BenefitType.QUARTERLY -> (month0Indexed + 1) % 3 == 0
            BenefitType.SEMI_ANNUAL -> (month0Indexed + 1) % 6 == 0
            BenefitType.ANNUAL -> month0Indexed == 11
        }
    }

    fun getPeriodIdentifier(type: BenefitType, year: String, month0Indexed: Int): String {
        return when (type) {
            BenefitType.MONTHLY -> {
                val m = month0Indexed + 1
                "$year-${m.toString().padStart(2, '0')}"
            }
            BenefitType.QUARTERLY -> {
                val quarter = (month0Indexed / 3) + 1
                "$year-Q$quarter"
            }
            BenefitType.SEMI_ANNUAL -> {
                val half = if (month0Indexed < 6) "H1" else "H2"
                "$year-$half"
            }
            BenefitType.ANNUAL -> {
                "$year-Annual"
            }
        }
    }

    fun getNotificationMessage(benefitName: String, type: BenefitType, calendar: Calendar): String {
        return when (type) {
            BenefitType.MONTHLY -> {
                val monthName = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: ""
                "$benefitName is still unused for $monthName"
            }
            BenefitType.QUARTERLY -> {
                val quarter = (calendar.get(Calendar.MONTH) / 3) + 1
                "$benefitName is still unused for Q$quarter"
            }
            BenefitType.SEMI_ANNUAL -> {
                "$benefitName is still unused for this half of the year"
            }
            BenefitType.ANNUAL -> {
                "$benefitName is still unused for this year"
            }
        }
    }
}

class UnusedCreditWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AmexApplication
        val database = app.database
        val cardDao = database.cardDao()
        val benefitDao = database.benefitDao()
        val usageHistoryDao = database.usageHistoryDao()

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"))
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)

        // Only run on the 20th of the month
        if (dayOfMonth != 20) {
            return Result.success()
        }

        val year = calendar.get(Calendar.YEAR).toString()
        val month0Indexed = calendar.get(Calendar.MONTH)

        val cards = cardDao.getAllCardsDirect()
        for (card in cards) {
            val benefits = benefitDao.getBenefitsForCardDirect(card.id)
            for (benefit in benefits) {
                if (BenefitNotificationHelper.isLastMonthOfPeriod(benefit.type, month0Indexed)) {
                    val periodIdentifier = BenefitNotificationHelper.getPeriodIdentifier(benefit.type, year, month0Indexed)
                    val usage = usageHistoryDao.getUsageForPeriod(benefit.id, periodIdentifier)
                    if (usage == null) {
                        val message = BenefitNotificationHelper.getNotificationMessage(benefit.name, benefit.type, calendar)
                        sendNotification(card.name, message, benefit.id.toInt())
                    }
                }
            }
        }

        return Result.success()
    }

    private fun sendNotification(cardName: String, message: String, notificationId: Int) {
        val context = applicationContext
        val channelId = "benefit_notifications"
        val channelName = "Benefit Notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for unused credits"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$cardName Benefit")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT >= 33) {
                if (ActivityCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }
            notify(notificationId, builder.build())
        }
    }
}
