package app.slothie

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class SlothieApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NUDGES, "Sloth hour nudges", NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "A heads-up a few minutes before an hour you usually lose to the phone." },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NAP, "Nap mode", NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shown while Slothie is holding Do Not Disturb for you." },
        )
    }

    companion object {
        const val CHANNEL_NUDGES = "nudges"
        const val CHANNEL_NAP = "nap"
    }
}
