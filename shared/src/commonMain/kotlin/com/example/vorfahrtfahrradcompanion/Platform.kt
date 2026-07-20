package com.example.vorfahrtfahrradcompanion

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform