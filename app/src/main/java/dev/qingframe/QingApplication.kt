package dev.qingframe
import android.app.Application
import dev.qingframe.model.ModelLibrary
class QingApplication:Application(){override fun onCreate(){super.onCreate();ModelLibrary.get(this)}}
