package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.repository.PdfRepository
import com.example.utils.*

class PdfReaderApp : Application() {

    // Global Manual Dependency Injection container
    lateinit var database: AppDatabase
    lateinit var pdfRepository: PdfRepository
    lateinit var settingsDataStore: SettingsDataStore
    lateinit var pdfUtils: PdfUtils
    lateinit var ocrManager: OcrManager
    lateinit var translationManager: TranslationManager
    lateinit var fileManager: FileManager
    lateinit var permissionManager: PermissionManager
    lateinit var appBiometricManager: AppBiometricManager
    lateinit var aiManager: AIManager

    override fun onCreate() {
        super.onCreate()
        
        // Initialize singletons
        database = AppDatabase.getDatabase(this)
        pdfRepository = PdfRepository(this, database)
        settingsDataStore = SettingsDataStore(this)
        pdfUtils = PdfUtils(this)
        ocrManager = OcrManager(this)
        translationManager = TranslationManager(this)
        fileManager = FileManager(this)
        permissionManager = PermissionManager(this)
        appBiometricManager = AppBiometricManager(this)
        aiManager = AIManager(this)
    }
}
