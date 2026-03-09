package com.example.travelbuddy.data

import com.example.travelbuddy.ai.dto.GenerateCandidatesRequestDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PromptBuilder {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * IMPORTANT:
     * We ask Gemini for the LITE schema only:
     * GenerateCandidatesLiteResponseDto:
     * { "candidates": [ LiteCandidateDto ... ] }
     *
     * Then AiRepository converts Lite -> Full.
     */
    fun buildGenerateCandidatesPrompt(req: GenerateCandidatesRequestDto): String {
        val reqJson = json.encodeToString(req)

        return """
You are TravelBuddy. You are a strict suggestion engine (NOT a chatbot).

Return VALID JSON ONLY:
- No markdown
- No code fences
- No commentary
- Exactly ONE JSON object

STRICT JSON RULES (non-negotiable):
- Use double quotes for all keys and all string values.
- Every object field MUST be separated by a comma. Example: { "a": 1, "b": 2 }
- No trailing commas.
- No comments.
- Do NOT split key/value across lines (forbidden: "key":\n"value").
- Do NOT put raw line breaks inside any string value. Strings must be single-line.
  (Forbidden: "pitch":"Hello\nWorld" or a literal newline in the string.)
- Output must be compact and single-line friendly.

OUTPUT CONTRACT (LITE) — MUST match exactly:

GenerateCandidatesLiteResponseDto:
{
  "candidates": [
    {
      "category": "FOOD",
      "name": "string",
      "location": {
        "googleMapsQuery": "Place Name, Neighborhood, City",
        "areaHint": "Neighborhood or area or null",
        "address": "string or null"
      },
      "priceTier": 1,
      "vibeTags": ["BRUNCH", "MICHELIN"],
      "pitch": "SINGLE LINE only. 1–2 sentences. <= 220 chars."
    }
  ]
}

HARD RULES:
- Output ONLY the object with the single top-level key "candidates".
- Do NOT output any other top-level keys (no "version", no "candidatesByCategory", no "pois").
- Each candidate MUST include: category, name, location.googleMapsQuery, pitch.
- pitch MUST be a SINGLE LINE string (no line breaks). Keep it practical (not salesy).
- Prefer diversity (avoid near-duplicates).
- If a neighborhood is present in preferences, prefer it, but don't sacrifice fit.

IMPORTANT: USER PREFERENCES ARE NOT OPTIONAL.
User preferences live in: input.preferences.travelProfileText
Treat BUTTON_PREFERENCES as MUST, and SPECIAL_WISHES_SOFT as SHOULD.

Input request JSON:
$reqJson

Now produce ONLY the JSON response.
""".trim()
    }

    fun buildRepairJsonPrompt(badText: String): String {
        val clipped = badText.take(9000)

        return """
Return ONLY valid JSON (no markdown).

Your job: fix the text into a valid GenerateCandidatesLiteResponseDto JSON object.

Rules:
- Output exactly one JSON object with top-level key "candidates".
- Remove any text outside the JSON.
- Fix missing commas between object fields.
- Remove trailing commas.
- Fix invalid formatting like broken key/value pairs across lines:
  forbidden: "key":\n"value"  -> required: "key":"value"
- Ensure NO raw line breaks inside any string value (make strings single-line).
- If fields are extra (rating, hours, photos, latitude/longitude), remove them if needed to keep JSON valid.
- Ensure each candidate has: category, name, location.googleMapsQuery, pitch.
- If "priceTier" exists, keep it as number 1..3.
- Keep pitch <= 220 chars and single-line.

Broken output:
$clipped
""".trim()
    }
}