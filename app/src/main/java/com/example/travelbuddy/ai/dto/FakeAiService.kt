package com.example.travelbuddy.ai.dto

/**
 * Simple local fake for demos/tests.
 * Produces a valid GenerateCandidatesResponseDto JSON string and decodes it.
 */
object FakeAiService {

    fun generateCandidates(city: String = "London"): GenerateCandidatesResponseDto {
        val now = System.currentTimeMillis() / 1000L

        val resp = GenerateCandidatesResponseDto(
            version = "1.0",
            city = city,
            generatedAtUtc = now,
            candidatesByCategory = mapOf(
                CategoryDto.FOOD to listOf(
                    CandidateDto(
                        candidateId = "fake_food_1",
                        category = CategoryDto.FOOD,
                        name = "Dishoom Covent Garden",
                        pitch = "Bombay café vibes with a killer bacon naan roll — great brunch energy.",
                        durationMin = 90,
                        bestTimeOfDay = TimeOfDayDto.ANY,
                        costTier = 2,
                        vibeTags = listOf("INDIAN", "BRUNCH"),
                        social = null,
                        location = LocationRefDto(
                            displayName = "Dishoom Covent Garden",
                            city = city,
                            areaHint = "Covent Garden",
                            addressHint = null,
                            googleMapsQuery = "Dishoom Covent Garden, $city",
                            lat = null,
                            lng = null,
                            googlePlaceId = null,
                            confidence = 0.7f
                        )
                    )
                )
            ),
            globalTips = listOf("Book popular spots ahead on weekends.")
        )

        val jsonText = AiJson.encode(resp)
        return AiJson.decode<GenerateCandidatesResponseDto>(jsonText)
    }
}