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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import com.example.data.model.GenerationUiState
import com.example.ui.MainViewModel
import com.example.ui.components.GenerationProgressDialog
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricPink
import com.example.ui.theme.NeonViolet

@Composable
fun StoryStudioScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val storyIdea by viewModel.storyIdea.collectAsState()
    val storyScenes by viewModel.storyScenes.collectAsState()
    val isGeneratingScenes by viewModel.isGeneratingStoryScenes.collectAsState()
    val isBengali by viewModel.isBengali.collectAsState()
    val generationState by viewModel.generationState.collectAsState()

    if (generationState is GenerationUiState.Generating) {
        val gen = generationState as GenerationUiState.Generating
        GenerationProgressDialog(
            progress = gen.progress,
            stage = gen.stage,
            stageBn = gen.stageBn,
            isBengali = isBengali,
            onDismiss = { viewModel.dismissGenerationState() }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("story_studio_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp)
    ) {
        // Header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MovieCreation,
                    contentDescription = "Story Studio",
                    tint = ElectricPink,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isBengali) "এআই স্টোরি টু ভিডিও স্টুডিও" else "AI Story-to-Video Studio",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isBengali) "গল্পের সারাংশ লিখে সম্পূর্ণ সিনেমা সিন্থেসিস করুন" else "Turn multi-scene story ideas into cinematic movies",
                        fontSize = 12.sp,
                        color = CyberCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Story Input Field
            OutlinedTextField(
                value = storyIdea,
                onValueChange = { viewModel.setStoryIdea(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("story_idea_input"),
                placeholder = {
                    Text(
                        text = if (isBengali)
                            "আপনার গল্প বা সিনেমার ধারণা লিখুন (যেমন: একজন নভোচারী মঙ্গল গ্রহে প্রাচীন এলিয়েন পিরামিড আবিষ্কার করে)..."
                        else
                            "Describe your narrative movie idea...",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CinemaSurface,
                    unfocusedContainerColor = CinemaSurface,
                    focusedBorderColor = ElectricPink,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                maxLines = 4,
                minLines = 3
            )

            Spacer(modifier = Modifier.height(12.dp))

            // AI Director Breakdown Button
            Button(
                onClick = { viewModel.generateStoryBreakdown() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("generate_story_scenes_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricPink.copy(alpha = 0.7f)),
                enabled = !isGeneratingScenes
            ) {
                if (isGeneratingScenes) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ElectricPink, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBengali) "সিনেমার দৃশ্য বিশ্লেষণ চলছে..." else "Director AI Generating Scenes...",
                        color = ElectricPink,
                        fontSize = 13.sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "AI Director",
                        tint = ElectricPink,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBengali) "🎬 এআই ডিরেক্টর সিনারিও তৈরি করুন" else "🎬 AI Director: Break into Scenes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Story Scenes List
        if (storyScenes.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBengali) "সিনারিও দৃশ্যসমূহ (${storyScenes.size})" else "Cinematic Scene Breakdown (${storyScenes.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AmberGlow.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isBengali) "৩টি ধারাবাহিক দৃশ্য" else "3 Continuous Scenes",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            color = AmberGlow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            items(storyScenes) { scene ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(NeonViolet),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${scene.sceneNumber}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = scene.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Angle",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = scene.cameraAngle,
                                    fontSize = 11.sp,
                                    color = CyberCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = scene.visualPrompt,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Generate Story Movie Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.generateStoryVideo() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(listOf(ElectricPink, NeonViolet, CyberCyan))
                        )
                        .testTag("render_story_video_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Render",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBengali) "🎞️ সম্পূর্ণ স্টোরি ভিডিও রেন্ডার করুন" else "🎞️ Render Full Story Movie",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
