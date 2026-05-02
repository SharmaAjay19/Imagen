package com.ajsharm.imagen

import android.app.Application
import com.ajsharm.imagen.di.ServiceLocator

class ImagenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
