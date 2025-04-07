package com.bebras123.notifyaod

import androidx.compose.runtime.mutableStateListOf
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val logMessages = mutableStateListOf<String>()

fun log(message: String) {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    val logMessage = "[$timestamp] $message"

    logMessages.add(logMessage)

    // Keep only the latest 100 logs
    if (logMessages.size > 100) {
        logMessages.removeAt(0) // Remove the oldest log
    }

}
