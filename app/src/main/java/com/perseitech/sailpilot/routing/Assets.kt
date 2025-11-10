package com.perseitech.sailpilot.routing

import android.content.Context

object Assets {
    fun loadText(context: Context, assetPath: String): String =
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
}
