package com.example.myapplication.ui.library

import com.example.myapplication.data.model.MoveStatus

val MoveStatus.label: String
    get() = when (this) {
        MoveStatus.LEARNING -> "Learning"
        MoveStatus.IN_ARSENAL -> "In arsenal"
        MoveStatus.BATTLE_READY -> "Battle-ready"
    }
