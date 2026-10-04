package com.example.data.director

data class ParsedPromptDirective(
    val originalPrompt: String,
    val visualStyle: VisualStyleType,
    val visualStyleDescription: String,
    val primarySubject: String,
    val characters: String,
    val environment: String,
    val timeOfDay: String,
    val weather: String,
    val cameraDirection: String,
    val dialogue: String?,
    val soundDesign: String,
    val moodAtmosphere: String,
    val enhancedCinematicPrompt: String
)

enum class VisualStyleType(val label: String, val badge: String) {
    LIVE_ACTION_REALISTIC("Live-Action Realistic", "Ultra-Realistic"),
    CARTOON_3D("3D Cartoon Animation", "3D Cartoon"),
    ANIME("Anime Animation", "Anime"),
    HORROR_CINEMATIC("Cinematic Horror", "Horror"),
    DOCUMENTARY("Realistic Documentary", "Documentary"),
    FANTASY_ANIMATION("Epic Fantasy", "Fantasy"),
    SCI_FI_CYBERPUNK("Sci-Fi Cyberpunk", "Cyberpunk")
}

object AiPromptDirector {

    fun analyzeAndDirect(userPrompt: String): ParsedPromptDirective {
        val clean = userPrompt.trim()
        val lower = clean.lowercase()

        // 1. Dialogue Extraction (quotes or spoken indicators)
        val dialogue = extractDialogue(clean)

        // 2. Visual Style Classification
        val style = detectStyle(lower)

        // 3. Time of Day Detection
        val timeOfDay = when {
            lower.contains("midnight") || lower.contains("রাত") || lower.contains("মধ্যরাত") || lower.contains("night") -> "midnight darkness"
            lower.contains("sunset") || lower.contains("সূর্যাস্ত") || lower.contains("dusk") || lower.contains("golden hour") -> "warm golden sunset"
            lower.contains("sunrise") || lower.contains("সূর্যোদয়") || lower.contains("ভোর") || lower.contains("dawn") -> "soft early sunrise light"
            lower.contains("sunny") || lower.contains("রোদ") || lower.contains("দুপুর") || lower.contains("day") -> "natural daytime lighting"
            else -> "cinematic natural lighting"
        }

        // 4. Weather & Atmosphere Detection
        val weather = when {
            lower.contains("rain") || lower.contains("বৃষ্টি") -> "heavy rain with wet reflective surfaces"
            lower.contains("fog") || lower.contains("কুয়াশা") || lower.contains("mist") -> "dense atmospheric fog and mist"
            lower.contains("cloud") || lower.contains("মেঘ") -> "overcast sky with dramatic clouds"
            lower.contains("storm") || lower.contains("ঝড়") -> "turbulent windy storm"
            else -> "clear atmospheric depth"
        }

        // 5. Camera Direction
        val cameraDirection = when {
            lower.contains("tracking") -> "smooth tracking camera keeping pace with the subject"
            lower.contains("push-in") || lower.contains("zoom") || style == VisualStyleType.HORROR_CINEMATIC -> "slow tense push-in focusing into focal details"
            lower.contains("drone") || lower.contains("aerial") || lower.contains("fly") -> "wide sweeping aerial drone perspective"
            lower.contains("handheld") -> "organic handheld camera with subtle natural motion"
            lower.contains("pan") -> "slow cinematic horizontal pan across the environment"
            else -> when (style) {
                VisualStyleType.HORROR_CINEMATIC -> "slow creeping push-in camera building suspense"
                VisualStyleType.CARTOON_3D -> "dynamic animated camera with expressive framing"
                VisualStyleType.DOCUMENTARY -> "observational documentary steady camera"
                else -> "cinematic dolly shot with smooth parallax"
            }
        }

        // 6. Sound Design
        val soundDesign = when (style) {
            VisualStyleType.HORROR_CINEMATIC -> "eerie midnight wind, distant crickets, low sub-bass drone, subtle whispering ambience"
            VisualStyleType.CARTOON_3D -> "playful whimsical cartoon foley, light ambient breeze, cheerful melodic tones"
            VisualStyleType.DOCUMENTARY -> "authentic diegetic environmental audio, gentle water ripples, nature sounds"
            VisualStyleType.SCI_FI_CYBERPUNK -> "distant thunder, wet asphalt tire spray, synthesized cyberpunk hum"
            else -> "realistic environmental audio and subtle cinematic score"
        }

        // 7. Visual Style Description
        val styleDesc = when (style) {
            VisualStyleType.CARTOON_3D -> "High-end 3D character animation, expressive acting, charming stylized textures, Pixar-inspired cinematic lighting, smooth 60fps motion"
            VisualStyleType.HORROR_CINEMATIC -> "Ultra-realistic cinematic horror footage, chilling shadows, realistic paranormal atmosphere, volumetric fog, disturbing supernatural tension"
            VisualStyleType.ANIME -> "Masterpiece anime feature film aesthetic, hand-drawn anime line-art, vibrant painted skies, Studio Ghibli inspired lighting"
            VisualStyleType.DOCUMENTARY -> "Authentic documentary cinematography, raw natural lighting, unvarnished real-world realism, high-definition textural clarity"
            VisualStyleType.FANTASY_ANIMATION -> "Grand high-fantasy aesthetic, mythical atmosphere, volumetric golden rays, cinematic scale and majesty"
            VisualStyleType.SCI_FI_CYBERPUNK -> "Futuristic cyberpunk cinematography, neon reflections on wet streets, volumetric holograms, anamorphic lens flares"
            VisualStyleType.LIVE_ACTION_REALISTIC -> "Photorealistic 8K live-action footage, natural skin textures, authentic clothing physics, 35mm lens depth of field"
        }

        // 8. Compose Enhanced Cinematic Prompt strictly preserving user's core intent
        val enhancedPrompt = buildString {
            append("$clean. ")
            append("Visual Style: $styleDesc. ")
            append("Atmosphere: $timeOfDay, $weather. ")
            append("Camera: $cameraDirection. ")
            if (!dialogue.isNullOrBlank()) {
                append("Spoken Dialogue: \"$dialogue\". ")
            }
            append("Preserve all characters, clothing, actions, and story elements strictly as described.")
        }

        return ParsedPromptDirective(
            originalPrompt = clean,
            visualStyle = style,
            visualStyleDescription = styleDesc,
            primarySubject = clean.take(40),
            characters = clean,
            environment = weather,
            timeOfDay = timeOfDay,
            weather = weather,
            cameraDirection = cameraDirection,
            dialogue = dialogue,
            soundDesign = soundDesign,
            moodAtmosphere = "$timeOfDay with $weather",
            enhancedCinematicPrompt = enhancedPrompt
        )
    }

    private fun extractDialogue(prompt: String): String? {
        // Matches "..." or '...' or Bengali quotes
        val quoteRegex = Regex("[\"“']([^\"”']+)[\"”']")
        val match = quoteRegex.find(prompt)
        if (match != null && match.groupValues[1].isNotBlank()) {
            return match.groupValues[1].trim()
        }

        // Check for dialogue indicator keywords
        val indicators = listOf("whispers:", "says:", "dialogue:", "বলে:", "বলেছে:", "বলল:")
        for (ind in indicators) {
            val idx = prompt.indexOf(ind, ignoreCase = true)
            if (idx != -1) {
                val candidate = prompt.substring(idx + ind.length).trim()
                if (candidate.isNotBlank()) {
                    return candidate.take(80)
                }
            }
        }
        return null
    }

    private fun detectStyle(lower: String): VisualStyleType {
        return when {
            lower.contains("3d cartoon") || lower.contains("cartoon") || lower.contains("pixar") ||
            lower.contains("funny cartoon") || lower.contains("কার্টুন") || lower.contains("3d অ্যানিমেশন") -> {
                VisualStyleType.CARTOON_3D
            }
            lower.contains("horror") || lower.contains("ghost") || lower.contains("ভুত") ||
            lower.contains("ভূত") || lower.contains("scary") || lower.contains("creepy") ||
            lower.contains("haunted") || lower.contains("terrifying") || lower.contains("ভয়ানক") -> {
                VisualStyleType.HORROR_CINEMATIC
            }
            lower.contains("documentary") || lower.contains("ডকুমেন্টারি") || lower.contains("real life footage") -> {
                VisualStyleType.DOCUMENTARY
            }
            lower.contains("anime") || lower.contains("manga") || lower.contains("অ্যানিমে") || lower.contains("ghibli") -> {
                VisualStyleType.ANIME
            }
            lower.contains("dragon") || lower.contains("castle") || lower.contains("fantasy") ||
            lower.contains("ড্রাগন") || lower.contains("ফ্যান্টাসি") || lower.contains("mythical") -> {
                VisualStyleType.FANTASY_ANIMATION
            }
            lower.contains("cyberpunk") || lower.contains("cyber") || lower.contains("neon") ||
            lower.contains("সাইবার") || lower.contains("futuristic") -> {
                VisualStyleType.SCI_FI_CYBERPUNK
            }
            else -> VisualStyleType.LIVE_ACTION_REALISTIC
        }
    }
}
