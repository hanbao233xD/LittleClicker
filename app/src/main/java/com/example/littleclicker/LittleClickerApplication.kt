package com.example.littleclicker

import android.app.Application
import com.example.littleclicker.autoclick.AutoClickCoordinator

class LittleClickerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AutoClickCoordinator.initialize(this)
    }
}
