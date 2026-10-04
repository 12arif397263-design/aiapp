package com.example

import com.example.data.director.AiPromptDirector
import com.example.data.director.VisualStyleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptDirectorTest {

    @Test
    fun test1_bengaliVillageHorrorGhost() {
        val prompt = "Create a 10-second ultra-realistic live-action Bengali village horror video. A terrifying female ghost with fiery red eyes slowly appears outside an abandoned house at midnight. Long tangled black hair, pale cracked skin, torn dirty sari, thick fog, moonlight, cinematic horror atmosphere. She whispers: \"পেছনে তাকিও না।\""

        val directive = AiPromptDirector.analyzeAndDirect(prompt)

        assertEquals(VisualStyleType.HORROR_CINEMATIC, directive.visualStyle)
        assertEquals("পেছনে তাকিও না।", directive.dialogue)
        assertTrue(directive.enhancedCinematicPrompt.contains("পেছনে তাকিও না।"))
        assertTrue(directive.timeOfDay.contains("midnight"))
    }

    @Test
    fun test2_funny3dCartoonBoyMangoTree() {
        val prompt = "Create a 10-second funny 3D cartoon video of a little Bengali boy secretly eating mangoes from a tree while his mother searches for him below."

        val directive = AiPromptDirector.analyzeAndDirect(prompt)

        assertEquals(VisualStyleType.CARTOON_3D, directive.visualStyle)
        assertTrue(directive.enhancedCinematicPrompt.contains("3D"))
        assertTrue(directive.enhancedCinematicPrompt.contains("mangoes"))
    }

    @Test
    fun test3_cinematicRealisticSportsCarRainyCity() {
        val prompt = "Create a 10-second cinematic realistic video of a black sports car driving through a rainy city at night, neon reflections on the wet road, tracking camera, realistic headlights and engine sound."

        val directive = AiPromptDirector.analyzeAndDirect(prompt)

        assertTrue(directive.enhancedCinematicPrompt.contains("sports car"))
        assertTrue(directive.cameraDirection.contains("tracking"))
        assertTrue(directive.weather.contains("rain"))
    }

    @Test
    fun test4_fantasyDragonCastleSunset() {
        val prompt = "Create a 10-second fantasy animation of a huge dragon flying over an ancient castle surrounded by clouds at sunset."

        val directive = AiPromptDirector.analyzeAndDirect(prompt)

        assertEquals(VisualStyleType.FANTASY_ANIMATION, directive.visualStyle)
        assertTrue(directive.timeOfDay.contains("sunset"))
        assertTrue(directive.enhancedCinematicPrompt.contains("dragon"))
    }

    @Test
    fun test5_realisticDocumentaryFishermanSunrise() {
        val prompt = "Create a 10-second realistic documentary-style video of a fisherman rowing a wooden boat across a quiet river at sunrise."

        val directive = AiPromptDirector.analyzeAndDirect(prompt)

        assertEquals(VisualStyleType.DOCUMENTARY, directive.visualStyle)
        assertTrue(directive.timeOfDay.contains("sunrise"))
        assertTrue(directive.enhancedCinematicPrompt.contains("fisherman"))
    }

    @Test
    fun testNoStaleStateBetweenGenerations() {
        val promptA = "Create a ghost in a village."
        val directiveA = AiPromptDirector.analyzeAndDirect(promptA)

        val promptB = "Create a tiger in a jungle."
        val directiveB = AiPromptDirector.analyzeAndDirect(promptB)

        // Ensure generation B has no ghost, village, or horror residue from A
        assertTrue(directiveB.enhancedCinematicPrompt.contains("tiger"))
        assertTrue(!directiveB.enhancedCinematicPrompt.contains("ghost"))
        assertTrue(!directiveB.enhancedCinematicPrompt.contains("village"))
    }
}
