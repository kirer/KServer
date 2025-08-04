package com.github.kirer.appk

import android.app.Application
import timber.log.Timber

/**
 * App-K应用程序类
 * 负责全局初始化
 */
class AppKApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化日志
        Timber.plant(Timber.DebugTree())
        
        Timber.d("App-K Application started")
    }
}
