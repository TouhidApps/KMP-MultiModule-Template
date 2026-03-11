package com.touhidapps.mmca

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform