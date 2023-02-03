package com.m3u.app.navigation

sealed interface Destination {
    data class Subscription(
        val url: String,
    ) : Destination

    data class Live(
        val id: Int,
    ) : Destination
}