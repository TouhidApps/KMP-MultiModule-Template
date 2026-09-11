package com.touhidapps.kmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
