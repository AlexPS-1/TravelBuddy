package com.example.travelbuddy.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.travelbuddy.ai.dto.LocationRefDto

object MapsIntents {

    fun openLocationSearch(context: Context, location: LocationRefDto) {
        val q = location.googleMapsQuery?.trim().orEmpty()
        if (q.startsWith("http://") || q.startsWith("https://")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(q))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }

        val query = q.ifBlank {
            listOfNotNull(location.displayName, location.areaHint, location.city)
                .joinToString(", ")
                .ifBlank { location.displayName }
        }

        val uri = Uri.parse("geo:0,0?q=" + Uri.encode(query))
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
