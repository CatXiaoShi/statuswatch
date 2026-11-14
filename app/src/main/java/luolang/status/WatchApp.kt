package luolang.status

import android.app.Application

class WatchApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile
        lateinit var instance: WatchApp
            private set
    }
}
