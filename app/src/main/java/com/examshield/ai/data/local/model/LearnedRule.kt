package com.examshield.ai.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learned_rules")
data class LearnedRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pattern: String, // e.g. "MAC_OUI:00:1A:2B" or "NAME_REGEX:Earbud.*"
    val detectedType: String, // e.g. "NANO_EARPIECE"
    val confidenceBoost: Int,
    val learnedAt: Long = System.currentTimeMillis()
)
