package com.example.myapplication

import android.app.Application
import com.example.myapplication.data.AppDatabase

class BreakingApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
}
