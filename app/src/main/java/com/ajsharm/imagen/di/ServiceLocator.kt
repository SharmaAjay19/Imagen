package com.ajsharm.imagen.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.ajsharm.imagen.data.AzureImageClient
import com.ajsharm.imagen.data.BackupManager
import com.ajsharm.imagen.data.ImageStorage
import com.ajsharm.imagen.data.ImagenDatabase
import com.ajsharm.imagen.data.PrefsStore
import com.ajsharm.imagen.data.Repository
import com.ajsharm.imagen.data.SecureConfigStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val Context.appDataStore by preferencesDataStore(name = "imagen_prefs")

object ServiceLocator {
    lateinit var appContext: Context
        private set
    lateinit var db: ImagenDatabase
        private set
    lateinit var repository: Repository
        private set
    lateinit var imageStorage: ImageStorage
        private set
    lateinit var secureConfig: SecureConfigStore
        private set
    lateinit var prefs: PrefsStore
        private set
    lateinit var azureClient: AzureImageClient
        private set
    lateinit var backupManager: BackupManager
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        db = Room.databaseBuilder(appContext, ImagenDatabase::class.java, "imagen.db")
            .fallbackToDestructiveMigration()
            .build()
        imageStorage = ImageStorage(appContext)
        secureConfig = SecureConfigStore(appContext)
        prefs = PrefsStore(appContext.appDataStore)
        val http = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .build()
        azureClient = AzureImageClient(http, secureConfig)
        repository = Repository(db.sessionDao(), db.messageDao(), imageStorage)
        backupManager = BackupManager(appContext, db, imageStorage)
    }
}
