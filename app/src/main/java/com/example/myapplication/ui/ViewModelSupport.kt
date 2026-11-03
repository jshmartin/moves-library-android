package com.example.myapplication.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.myapplication.BreakingApp

/** The app instance, for building ViewModels inside a `viewModelFactory { initializer { } }`. */
fun CreationExtras.breakingApp(): BreakingApp = this[APPLICATION_KEY] as BreakingApp
