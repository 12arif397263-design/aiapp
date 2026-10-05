package com.example.data.model

data class VideoStyle(
    val id: String,
    val name: String,
    val nameBn: String,
    val promptSuffix: String,
    val colorHex: Long,
    val tag: String
)

data class CameraMotion(
    val id: String,
    val name: String,
    val nameBn: String,
    val promptDesc: String
)

data class AspectRatioOption(
    val id: String,
    val label: String,
    val widthRatio: Int,
    val heightRatio: Int,
    val resolutionWidth: Int,
    val resolutionHeight: Int
)

data class AiVideoModelOption(
    val id: String,
    val name: String,
    val nameBn: String,
    val description: String,
    val descriptionBn: String,
    val badge: String,
    val isCloud: Boolean,
    val modelTag: String,
    val iconName: String
)

data class SystemArchitectureOption(
    val id: String,
    val title: String,
    val titleBn: String,
    val subtitle: String,
    val subtitleBn: String,
    val badge: String,
    val iconName: String
)

data class PresetPrompt(
    val title: String,
    val titleBn: String,
    val prompt: String,
    val styleId: String,
    val motionId: String,
    val category: String
)

data class StoryScene(
    val sceneNumber: Int,
    val title: String,
    val visualPrompt: String,
    val cameraAngle: String,
    val durationSec: Int = 3
)

sealed interface GenerationUiState {
    data object Idle : GenerationUiState
    data class EnhancingPrompt(val originalPrompt: String) : GenerationUiState
    data class Generating(val progress: Float, val stage: String, val stageBn: String) : GenerationUiState
    data class Success(val videoId: Long, val videoPath: String) : GenerationUiState
    data class Error(val message: String, val messageBn: String) : GenerationUiState
}

object VideoPresets {

    val MODELS = listOf(
        AiVideoModelOption(
            id = "veo_3_1_fast",
            name = "Google Veo 3.1 Fast",
            nameBn = "গুগল ভিও ৩.১ ফাস্ট (Veo Video)",
            description = "Official Google DeepMind Video Model - Real cloud AI generated video via Veo API",
            descriptionBn = "গুগল ডিপমাইন্ডের অফিসিয়াল জেনারেটিভ এআই ভিডিও এপিআই",
            badge = "Veo 3.1 Fast",
            isCloud = true,
            modelTag = "veo-3.1-fast-generate-preview",
            iconName = "veo"
        ),
        AiVideoModelOption(
            id = "veo_3_1_hd",
            name = "Google Veo 3.1 HD Cinema",
            nameBn = "গুগল ভিও ৩.১ এইচডি সিনেমা",
            description = "High definition cinematic AI video generation via Veo Cloud API",
            descriptionBn = "ভিও ক্লাউড এপিআইয়ের মাধ্যমে এইচডি সিনেমাটিক এআই ভিডিও",
            badge = "Veo 3.1 HD",
            isCloud = true,
            modelTag = "veo-3.1-generate-preview",
            iconName = "movie"
        ),
        AiVideoModelOption(
            id = "on_device_neural",
            name = "On-Device Local Engine (Offline Demo)",
            nameBn = "অন-ডিভাইস লোকাল ইঞ্জিন (অফলাইন ডেমো)",
            description = "Local procedural H.264 engine on device. Works offline without internet or API key",
            descriptionBn = "ফোনের লোকাল হার্ডওয়্যার ইঞ্জিন। ইন্টারনেট বা এপিআই কি ছাড়া অফলাইন ডেমো",
            badge = "Offline Demo",
            isCloud = false,
            modelTag = "local-h264",
            iconName = "bolt"
        )
    )

    val ARCHITECTURES = listOf(
        SystemArchitectureOption(
            id = "cloud_first",
            title = "Google Cloud AI Architecture",
            titleBn = "গুগল ক্লাউড এআই আর্কিটেকচার",
            subtitle = "Direct Google DeepMind Veo & Gemini API endpoints for cloud-generated AI video",
            subtitleBn = "সরাসরি গুগল ক্লাউড জেমিনাই ও ভিও এপিআই সংযোগ",
            badge = "Cloud Native",
            iconName = "cloud"
        ),
        SystemArchitectureOption(
            id = "hybrid",
            title = "Hybrid Cloud-Edge Cinema",
            titleBn = "হাইব্রিড ক্লাউড-এজ সিনেমা পাইপলাইন",
            subtitle = "Cloud AI visual scene generation coupled with on-device H.264 video hardware encoding",
            subtitleBn = "ক্লাউড এআই ভিজ্যুয়াল জেনারেশন + অন-ডিভাইস এমপি৪ হার্ডওয়্যার এনকোডিং",
            badge = "Default / Fast",
            iconName = "memory"
        ),
        SystemArchitectureOption(
            id = "edge_only",
            title = "Edge / On-Device Pipeline",
            titleBn = "সম্পূর্ণ অন-ডিভাইস এজ পাইপলাইন",
            subtitle = "100% On-device rendering. Zero internet consumption, zero API quota, instant creation",
            subtitleBn = "১০০% অফলাইন অন-ডিভাইস রেন্ডারিং। কোনো ডেটা খরচ নেই, কোনো কোটা বাধা নেই",
            badge = "Zero-Quota",
            iconName = "speed"
        )
    )

    val STYLES = listOf(
        VideoStyle(
            id = "photoreal",
            name = "8K Cinematic Film",
            nameBn = "৮কে সিনেমাটিক ফিল্ম",
            promptSuffix = "photorealistic 8k, cinematic lighting, 35mm lens, shallow depth of field, blockbuster movie aesthetic, hyper-detailed",
            colorHex = 0xFF3B82F6,
            tag = "Realistic"
        ),
        VideoStyle(
            id = "cyberpunk",
            name = "Cyberpunk Neon",
            nameBn = "সাইবারপাঙ্ক নিয়ন",
            promptSuffix = "cyberpunk aesthetic, vibrant neon reflections, futuristic tech, rainy night, volumetric fog, octane render",
            colorHex = 0xFF8B5CF6,
            tag = "Sci-Fi"
        ),
        VideoStyle(
            id = "anime",
            name = "Anime & Manga",
            nameBn = "অ্যানিমে ও মাঙ্গা",
            promptSuffix = "makoto shinkai studio ghibli anime style, lush vibrant colors, painted sky, whimsical lighting, masterpiece",
            colorHex = 0xFFEC4899,
            tag = "Animation"
        ),
        VideoStyle(
            id = "pixar3d",
            name = "Pixar 3D Animation",
            nameBn = "পিক্সার ৩ডি অ্যানিমেশন",
            promptSuffix = "pixar disney 3d animated style, charming character design, warm subsurface scattering, expressive lighting, 4k render",
            colorHex = 0xFFF59E0B,
            tag = "3D"
        ),
        VideoStyle(
            id = "fantasy",
            name = "Dark Epic Fantasy",
            nameBn = "ডার্ক এপিক ফ্যান্টাসি",
            promptSuffix = "lord of the rings style, mythical glowing runes, epic mystical atmosphere, dark fantasy landscape, cinematic lighting",
            colorHex = 0xFF10B981,
            tag = "Fantasy"
        ),
        VideoStyle(
            id = "retro_vhs",
            name = "Retro 80s VHS",
            nameBn = "রেট্রো ৮০র দশক ভিএইচএস",
            promptSuffix = "1980s synthwave VHS footage, chromatic aberration, scanlines, analog film grain, retro nostalgia vibes",
            colorHex = 0xFFF43F5E,
            tag = "Retro"
        )
    )

    val MOTIONS = listOf(
        CameraMotion("orbit", "Drone Orbit", "ড্রোন অরবিট", "smooth 360-degree drone orbit around subject"),
        CameraMotion("zoom_in", "Dynamic Zoom In", "ডাইনামিক জুম ইন", "slow cinematic zoom-in focusing into focal details"),
        CameraMotion("pan_left", "Pan Left", "বাম দিকে প্যান", "smooth horizontal camera pan gliding from right to left"),
        CameraMotion("pan_right", "Pan Right", "ডান দিকে প্যান", "smooth horizontal camera pan gliding from left to right"),
        CameraMotion("fpv", "FPV Flythrough", "এফপিভি ফ্লাইট", "high-speed first-person drone swoop racing through environment"),
        CameraMotion("tilt_up", "Epic Tilt Up", "উপরে টিল্ট", "dramatic vertical camera tilt looking up towards the sky")
    )

    val ASPECT_RATIOS = listOf(
        AspectRatioOption("9:16", "9:16 Vertical (Shorts/Reels)", 9, 16, 720, 1280),
        AspectRatioOption("16:9", "16:9 Landscape (Cinema)", 16, 9, 1280, 720)
    )

    val PRESET_PROMPTS = listOf(
        PresetPrompt(
            title = "Cyberpunk Flying Car",
            titleBn = "সাইবারপাঙ্ক উড়ন্ত গাড়ি",
            prompt = "A sleek matte-black hover-car gliding through skyscraper canyons with neon holographic ads in a rainy night",
            styleId = "cyberpunk",
            motionId = "fpv",
            category = "Sci-Fi"
        ),
        PresetPrompt(
            title = "Sunset in Cox's Bazar",
            titleBn = "কক্সবাজারের সূর্যাস্ত",
            prompt = "Breathtaking golden hour sunset over Cox's Bazar beach waves with traditional wooden fishing boats and seagulls",
            styleId = "photoreal",
            motionId = "pan_right",
            category = "Scenic"
        ),
        PresetPrompt(
            title = "Space Nebula Warp",
            titleBn = "মহাকাশ নেবুলা ওয়ার্প",
            prompt = "Spaceship traveling faster than light through a glowing purple and turquoise cosmic nebula with distant spiraling galaxies",
            styleId = "cyberpunk",
            motionId = "zoom_in",
            category = "Sci-Fi"
        ),
        PresetPrompt(
            title = "Mystical Forest Spirit",
            titleBn = "রহস্যময় বনের আত্মা",
            prompt = "An ancient moss-covered tree awakening with glowing turquoise bioluminescent butterflies and gentle sakura petals",
            styleId = "anime",
            motionId = "orbit",
            category = "Fantasy"
        ),
        PresetPrompt(
            title = "Cute Robot Barista",
            titleBn = "মিষ্টি রোবট বারিস্তা",
            prompt = "A friendly miniature brass steampunk robot carefully brewing an aromatic cappuccino with latte art foam",
            styleId = "pixar3d",
            motionId = "zoom_in",
            category = "3D"
        ),
        PresetPrompt(
            title = "Epic Mountain Dragon",
            titleBn = "পাহাড়ের ড্রাগন",
            prompt = "A majestic dragon soaring above snow-capped mountain peaks bathed in early morning golden sunbeams",
            styleId = "fantasy",
            motionId = "orbit",
            category = "Fantasy"
        )
    )
}
