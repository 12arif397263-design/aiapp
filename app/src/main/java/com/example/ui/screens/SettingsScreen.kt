package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonViolet

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val totalVideos by viewModel.totalVideoCount.collectAsState()
    val isBengali by viewModel.isBengali.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = NeonViolet,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isBengali) "সেটিংস ও স্ট্যাটাস" else "Settings & Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Unlimited Membership VIP Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.horizontalGradient(listOf(AmberGlow, NeonViolet)),
                        RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CinemaSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AllInclusive,
                                contentDescription = "VIP",
                                tint = AmberGlow,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBengali) "আনলিমিটেড ফ্রি পাস" else "Unlimited VIP Pass",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EmeraldGreen.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isBengali) "লাইফটাইম ফ্রি" else "Lifetime Free",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGreen
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isBengali)
                            "আপনি যেকোনো সময় যতখুশি তত ভিডিও সম্পূর্ণ ফ্রিতে এইচডি এবং ৪কে ফরম্যাটে তৈরি ও ডাউনলোড করতে পারবেন। কোনো ওয়াটারমার্ক বা ক্রেডিট লিমিট নেই!"
                        else
                            "Generate and export unlimited high-definition AI videos anytime with no daily quotas, credits, or watermarks.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(
                            label = if (isBengali) "মোট ভিডিও জেনারেট" else "Total Rendered",
                            value = "$totalVideos"
                        )
                        StatItem(
                            label = if (isBengali) "দৈনিক লিমিট" else "Daily Limit",
                            value = "∞ Unlimited"
                        )
                        StatItem(
                            label = if (isBengali) "এক্সপোর্ট কোয়ালিটি" else "Max Quality",
                            value = "4K / 60 FPS"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Language Preference
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Language",
                            tint = CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isBengali) "ভাষা: বাংলা (Bangla)" else "Language: English",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (isBengali) "বাংলা ও ইংরেজির মধ্যে পরিবর্তন করুন" else "Toggle between Bengali and English",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Switch(
                        checked = isBengali,
                        onCheckedChange = { viewModel.toggleLanguage() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberCyan,
                            checkedTrackColor = CyberCyan.copy(alpha = 0.3f),
                            uncheckedThumbColor = NeonViolet,
                            uncheckedTrackColor = NeonViolet.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("language_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Engine Architecture & Features
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBengali) "সিস্টেম ইঞ্জিন আর্কিটেকচার" else "AI Synthesis Architecture",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    EngineFeatureRow(
                        title = "Google Gemini 3.5 Flash",
                        desc = if (isBengali) "সিনেমাটিক প্রম্পট বিশ্লেষণ ও স্ক্রিপ্ট ডিরেকশন" else "Cinematic prompt expansion & storyboard breakdown",
                        icon = Icons.Default.Memory,
                        color = NeonViolet
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    EngineFeatureRow(
                        title = "Veo 3.1 & Neural Video Synthesizer",
                        desc = if (isBengali) "এইচ.২৬৪ হার্ডওয়্যার এনকোডেড এমপি৪ ভিডিও পাইপলাইন" else "Real-time H.264 hardware canvas encoder with 3D camera physics",
                        icon = Icons.Default.Videocam,
                        color = CyberCyan
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    EngineFeatureRow(
                        title = "Zero-Quota Block Protection",
                        desc = if (isBengali) "কখনোই কোটা শেষ হওয়ার বাধা নেই, অবিরাম রেন্ডার সক্ষম" else "Guaranteed continuous generation with on-device fallback synthesis",
                        icon = Icons.Default.CheckCircle,
                        color = EmeraldGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Tips",
                            tint = AmberGlow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBengali) "ব্যবহারের বিশেষ টিপস" else "Pro Tips for Best Results",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val tips = if (isBengali) listOf(
                        "১. যেকোনো সাধারণ বাক্য লিখে '✨ এআই ম্যাজিক প্রম্পট' বাটনে চাপ দিন।",
                        "২. সাইবারপাঙ্ক, অ্যানিমে বা ৮কে সিনেমাটিক স্টাইল সিলেক্ট করুন।",
                        "৩. রেন্ডার হওয়ার পর 'গ্যালারিতে সেভ' বাটনে চাপ দিলে সরাসরি ফোনে সেভ হবে।",
                        "৪. সোশ্যাল মিডিয়ায় (ফেসবুক, ইউটিউব শর্টস, টিকটক) ৯:১৬ রেশিও ব্যবহার করুন।"
                    ) else listOf(
                        "1. Write a simple idea and tap '✨ AI Magic Enhance' for cinematic prompts.",
                        "2. Choose styles like Cyberpunk, Anime, or 8K Cinematic Film.",
                        "3. Use 'Save MP4' to export directly to your device gallery.",
                        "4. Select 9:16 aspect ratio for TikTok, Reels, and Shorts."
                    )

                    tips.forEach { tip ->
                        Text(
                            text = tip,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = CyberCyan
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun EngineFeatureRow(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f),
                lineHeight = 15.sp
            )
        }
    }
}
