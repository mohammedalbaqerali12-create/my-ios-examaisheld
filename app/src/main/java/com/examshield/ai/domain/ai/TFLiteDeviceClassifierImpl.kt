package com.examshield.ai.domain.ai

import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.model.DetectedObject
import com.examshield.ai.domain.model.DeviceType
import com.examshield.ai.domain.model.DistanceZone
import com.examshield.ai.domain.model.RiskLevel
import com.examshield.ai.domain.usecase.EstimateDistanceUseCase
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class TFLiteDeviceClassifierImpl @Inject constructor(
    private val estimateDistanceUseCase: EstimateDistanceUseCase,
    private val adaptiveLearningEngine: AdaptiveLearningEngine,
    private val aiIntelligenceService: AiIntelligenceService
) : DeviceClassifier {


    private suspend fun determineDeviceType(detectedObject: DetectedObject): DeviceType {
        val lowerName = detectedObject.name?.lowercase() ?: ""
        val manufacturer = OuiLookup.lookup(detectedObject.macAddress)
        val extra = detectedObject.extraMetadata
        val uuids = (extra["serviceUuids"] as? List<*>)?.map { it.toString().lowercase() } ?: emptyList()
        
        // 1. PHYSICAL THREAT DETECTION
        if (detectedObject.macAddress == "MAGNETIC_FIELD_ANOMALY") {
             val vector = detectedObject.sensorVector
             if (vector != null) {
                 val magnitude = kotlin.math.sqrt(vector[0]*vector[0] + vector[1]*vector[1] + vector[2]*vector[2])
                 return if (magnitude > 150) DeviceType.NANO_EARPIECE else DeviceType.MAGNETIC_ANOMALY
             }
             return DeviceType.MAGNETIC_ANOMALY
        }

        // 2. SURGICAL UUID DNA ANALYSIS (Astra V5)
        val isAudioUuids = uuids.any { it.contains("110b") || it.contains("110a") || it.contains("110e") || it.contains("fd5a") }
        val isWearableUuids = uuids.any { it.contains("180d") || it.contains("1812") || it.contains("fee7") || it.contains("fedd") }
        val isPhoneUuids = uuids.any { it.contains("fe9f") || it.contains("d061") || it.contains("0000fe9f") } // Google Fast Pair / Apple Continuity

        // 3. INFRASTRUCTURE SCANNING (Routers/Towers) - STRICTLY DROPPED LATER
        val isInfrastructure = lowerName.contains("router") || lowerName.contains("tp-link") ||
                               lowerName.contains("d-link") || lowerName.contains("asus") ||
                               (manufacturer?.lowercase()?.contains("tp-link") == true) ||
                               (manufacturer?.lowercase()?.contains("d-link") == true)
        if (isInfrastructure) return DeviceType.ROUTER_INFRASTRUCTURE

        // 4. SMARTPHONE SIGNATURE (REFINED V5)
        val isRandomizedMac = detectedObject.macAddress.length >= 2 && 
                               detectedObject.macAddress[1].lowercaseChar() in listOf('2', '6', 'a', 'e')
        val txPower = extra["txPower"] as? Int ?: -1
        val isConnectable = extra["isConnectable"] as? Boolean ?: false
        
        // Broaden: Many modern phones are randomized and connectable but might have weak TX power info
        val hasPhoneServices = uuids.any { it.contains("0000fe9f") || it.contains("0000fd64") || it.contains("0000feaf") || it.contains("0000feed") }
        val isModernPhone = (isRandomizedMac && isConnectable) || isPhoneUuids || hasPhoneServices || (isRandomizedMac && txPower > -75)

        // 5. SURGICAL CLASSIFICATION
        return when {
            // Priority 1: UUID DNA (Highest Accuracy)
            isAudioUuids -> DeviceType.WIRELESS_EARBUD
            isWearableUuids -> DeviceType.SMARTWATCH
            isPhoneUuids -> DeviceType.SMARTPHONE
            
            // Priority 2: Direct Name Match
            lowerName.contains("buds") || lowerName.contains("ear") || lowerName.contains("pod") || lowerName.contains("audio") 
                || lowerName.contains("headset") -> DeviceType.WIRELESS_EARBUD
            lowerName.contains("watch") || lowerName.contains("band") || lowerName.contains("fitbit") 
                || lowerName.contains("garmin") -> DeviceType.SMARTWATCH
            lowerName.contains("phone") || lowerName.contains("iphone") || lowerName.contains("galaxy") || lowerName.contains("pixel")
                || lowerName.contains("android") || lowerName.contains("samsung") || lowerName.contains("huawei") || lowerName.contains("oppo")
                || lowerName.startsWith("direct-") -> DeviceType.SMARTPHONE
            
            // Common Cheating Gear Signatures (ESP32, Serial Bluetooth modules, Tiny Transmitters)
            lowerName.contains("esp32") || lowerName.contains("hc-05") || lowerName.contains("hc-06") 
                || lowerName.contains("ble-uart") || lowerName.contains("serial") || (manufacturer?.lowercase()?.contains("espressif") == true) -> DeviceType.NANO_EARPIECE

            // Mac Address Manufacturer identification via AI Logic mapped below:
            manufacturer?.lowercase()?.let { mLower -> 
                 mLower.contains("apple") || mLower.contains("samsung") || mLower.contains("google") ||
                 mLower.contains("huawei") || mLower.contains("oppo") || mLower.contains("vivo") ||
                 mLower.contains("realme") || mLower.contains("honor") ||
                 mLower.contains("oneplus") || mLower.contains("motorola") || mLower.contains("xiaomi")
            } == true -> DeviceType.SMARTPHONE
            
            manufacturer?.lowercase()?.let { mLower ->
                mLower.contains("anker") || mLower.contains("jabra") || mLower.contains("bose") || mLower.contains("sony") ||
                mLower.contains("sennheiser") || mLower.contains("beats") || mLower.contains("edifier") 
            } == true -> DeviceType.WIRELESS_EARBUD
            
            manufacturer?.lowercase()?.let { mLower ->
                mLower.contains("garmin") || mLower.contains("fitbit") || mLower.contains("suunto") || mLower.contains("polar")
            } == true -> DeviceType.SMARTWATCH

            // Priority 3: Contextual logic based on manufacturer
            manufacturer != null -> {
                val mLower = manufacturer.lowercase()
                when {
                    mLower.contains("apple") || mLower.contains("samsung") || mLower.contains("google") ||
                    mLower.contains("huawei") || mLower.contains("oppo") || mLower.contains("vivo") ||
                    mLower.contains("oneplus") || mLower.contains("motorola") || mLower.contains("xiaomi") -> {
                         // Refine by context
                         if (isConnectable && txPower > -65) DeviceType.SMARTPHONE
                         else if (txPower < -70) DeviceType.WIRELESS_EARBUD
                         else DeviceType.SMARTWATCH
                    }
                    else -> if (isModernPhone) DeviceType.SMARTPHONE else DeviceType.SUSPICIOUS_UNKNOWN
                }
            }

            // Priority 4: Fallbacks (More aggressive for Astra V5)
            isModernPhone -> DeviceType.SMARTPHONE
            isRandomizedMac && isConnectable -> DeviceType.SMARTPHONE
            isRandomizedMac -> DeviceType.SMARTPHONE // In a strict exam environment, any randomized BLE is likely a phone/watch
            
            // Unnamed non-randomized BLE modules are highly suspicious
            detectedObject.isBle && detectedObject.name.isNullOrBlank() && !isRandomizedMac -> DeviceType.NANO_EARPIECE
            
            // Check for technical names that indicate serial/cheating gear
            lowerName.contains("serial") || lowerName.contains("uart") || lowerName.contains("com") 
                || lowerName.contains("tty") || lowerName.contains("bt-") -> DeviceType.NANO_EARPIECE

            else -> DeviceType.SUSPICIOUS_UNKNOWN
        }
    }

    override suspend fun classify(detectedObject: DetectedObject): ClassificationResult {
        val result = performBaseClassification(detectedObject)
        
        // --- ASTRA V5 SURGICAL NAMING ---
        // If the device has no name, generate a professional technical signature
        if (result.rawObject.name.isNullOrBlank()) {
            val manufacturer = OuiLookup.lookup(result.rawObject.macAddress)
            val surgicalName = generateProfessionalName(result, manufacturer)
            return result.copy(rawObject = result.rawObject.copy(name = surgicalName))
        }
        
        return result
    }

    private suspend fun performBaseClassification(detectedObject: DetectedObject): ClassificationResult {
        val context = adaptiveLearningEngine.analyze(detectedObject)
        val distance = estimateDistanceUseCase(detectedObject.signalStrengthRssi.toDouble())
        val zone = getDistanceZone(distance)
        val type = determineDeviceType(detectedObject)
        var confidence = calculateConfidence(type, detectedObject, zone)

        if (context.isFrequentlySeen) confidence -= 10 // Reduced penalty
        if (context.isNew) confidence += 20
        
        // AI Self-Learning: If we've seen this device many times in short bursts, boost threat assessment
        var selfLearningBoost = if (context.observationCount > 5 && context.isRepeatedlySeenRecently) 15 else 0
        
        // Behavioral Practice: Consistent predictable streams are high-risk indicators for cheating gear
        if (context.isConsistentStream) {
            selfLearningBoost += 20
        }
        
        confidence += selfLearningBoost
        
        // --- ASTRA NEXUS PRO: SELF-CORRECTION & STABILITY ---
        val penalty = adaptiveLearningEngine.getmacPenalty(detectedObject.macAddress)
        confidence -= penalty
        
        val stabilityBoost = (context.stability * 20).toInt()
        confidence += stabilityBoost

        val riskLevel = determineRiskLevel(type, zone, confidence + selfLearningBoost, context)
        
        // Generate Discovery Reason for transparency
        val baseReason = buildString {
            if (context.isNew) append("NEW_NODE ")
            if (context.isConsistentStream) append("BEHAVIOR_MATCH ")
            if (context.isRepeatedlySeenRecently) append("BURST_PATTERN ")
            if (detectedObject.isClassicBluetooth) append("CLASSIC_BT ")
            if (detectedObject.isBle) append("BLE_ADVERT ")
            if (detectedObject.macAddress.startsWith("MAGNETIC")) append("MAG_SENSOR ")
        }.trim()

        // --- ASTRA NEXUS: UNIFIED SYNERGY LOGIC ---
        var isNexusVerified = false
        var synergyScore = confidence
        var finalType = type
        var finalReason = baseReason.ifEmpty { "LOCAL_SIG" }
        
        if (type == DeviceType.SUSPICIOUS_UNKNOWN || confidence < 60) {
            val aiResponse = aiIntelligenceService.analyzeThreat(
                mac = detectedObject.macAddress,
                name = detectedObject.name,
                manufacturer = OuiLookup.lookup(detectedObject.macAddress),
                rssi = detectedObject.signalStrengthRssi,
                deviceType = type.name
            ).firstOrNull()
            
            if (aiResponse != null && !aiResponse.contains("IGNORE_SIGNAL") && aiResponse != "LOCAL_ONLY" && aiResponse != "AI_OFFLINE") {
                val upperResponse = aiResponse.uppercase()
                val cloudType = when {
                    upperResponse.contains("SMARTPHONE") -> DeviceType.SMARTPHONE
                    upperResponse.contains("SMARTWATCH") -> DeviceType.SMARTWATCH
                    upperResponse.contains("EARBUD") -> DeviceType.WIRELESS_EARBUD
                    upperResponse.contains("EARPIECE") -> DeviceType.NANO_EARPIECE
                    else -> null
                }
                
                if (cloudType != null) {
                    // Nexus Match: Local AI and Cloud AI agree or Cloud promotes Local
                    if (cloudType == finalType || finalType == DeviceType.SUSPICIOUS_UNKNOWN) {
                        isNexusVerified = true
                        synergyScore += 30
                        finalType = cloudType
                        finalReason = "(NEXUS: $aiResponse)"
                    } else {
                        finalReason = "(CLOUD_OVERRIDE: $aiResponse)"
                        finalType = cloudType
                        synergyScore += 15
                    }
                }
            }
        }

        // Sensor Fusion Synergy Boost (Aggressive Marking for Nexus Protocol)
        if (baseReason.contains("MAG_SENSOR") || (context.isConsistentStream && confidence > 70)) {
            synergyScore += 40
            isNexusVerified = true
            finalReason = "NEXUS_MATCH_VERIFIED"
        }
        
        // Behavioral Nexus: Recurring high-risk patterns
        if (context.observationCount > 3 && context.isRepeatedlySeenRecently && confidence > 80) {
            isNexusVerified = true
            synergyScore += 20
        }
        
        // ASTRA V11: AUTONOMOUS EVOLUTION
        if (context.learnedTypePromotion != null) {
            val learnedType = DeviceType.valueOf(context.learnedTypePromotion)
            if (learnedType != DeviceType.SUSPICIOUS_UNKNOWN) {
                finalType = learnedType
                isNexusVerified = true // Learned rules provide instant Nexus-level verification
                synergyScore += context.learnedSynergyBoost
                finalReason = "NEXUS_EVOLVED_MATCH"
            }
        }

        // ASTRA NEXUS: SUPERVISOR FEEDBACK OVERRIDE
        if (context.isMarkedFriendly) {
            confidence = 5
            synergyScore = 0
            isNexusVerified = false
            finalReason = "SUPERVISOR_MARKED_SAFE"
        }

        if (context.isMarkedCheating) {
            confidence = 100
            synergyScore = 100
            isNexusVerified = true
            finalReason = if (context.cheatingHitCount > 1) "REPEATED_CHEATING_PATTERN" else "SUPERVISOR_CONFIRMED_THREAT"
        }
        
        // Apply feedback-driven risk elevation (Astra Nexus Loop)
        confidence += context.feedbackRiskElevation
        synergyScore += context.feedbackRiskElevation

        // Trigger Autonomous Learning if Cloud AI verified a threat
        if (isNexusVerified && context.learnedTypePromotion.isNullOrBlank()) {
             // If we just verified it via Cloud AI and don't have a local rule yet, learn it!
             if (finalReason.contains("NEXUS:")) {
                 adaptiveLearningEngine.learnFromNexus(
                     ClassificationResult(
                         deviceType = finalType,
                         confidenceScore = confidence.coerceIn(0, 100),
                         distanceZone = zone,
                         estimatedDistanceMeters = distance.toFloat(),
                         riskLevel = determineRiskLevel(finalType, zone, confidence, context),
                         discoveryReason = finalReason,
                         rawObject = detectedObject,
                         isNexusVerified = isNexusVerified,
                         synergyScore = synergyScore.coerceIn(0, 100)
                     )
                 )
                 finalReason = "NEXUS_NEW_PATTERN_EVOLVED"
             }
        }
        
        return ClassificationResult(
            deviceType = finalType,
            confidenceScore = confidence.coerceIn(0, 100),
            distanceZone = zone,
            estimatedDistanceMeters = distance.toFloat(),
            riskLevel = determineRiskLevel(finalType, zone, confidence, context),
            discoveryReason = finalReason,
            rawObject = detectedObject,
            isNexusVerified = isNexusVerified,
            synergyScore = synergyScore.coerceIn(0, 100),
            feedback = when {
                context.isMarkedFriendly -> com.examshield.ai.domain.model.SupervisorFeedback.FRIENDLY
                context.isMarkedCheating -> com.examshield.ai.domain.model.SupervisorFeedback.CHEATING
                else -> com.examshield.ai.domain.model.SupervisorFeedback.PENDING
            }
        )
    }

    private fun generateProfessionalName(result: ClassificationResult, rawManufacturer: String?): String {
        val isFused = result.discoveryReason.contains("MAG_FUSION")
        val prefix = if (isFused) "FUSED_" else ""
        
        val rawName = result.rawObject.name
        if (!rawName.isNullOrBlank()) {
             // Astra V11: Format known names with a tactical prefix
             return "$prefix$rawName".uppercase()
        }

        // If hidden, deep-scan manufacturer OUI
        val manufacturer = rawManufacturer?.lowercase() ?: "Unknown"

        val brandPrefix = when {
            manufacturer.contains("apple") -> "APPLE_CORE"
            manufacturer.contains("samsung") -> "SAMSUNG_GALAXY"
            manufacturer.contains("google") -> "GOOGLE_PIXEL"
            manufacturer.contains("huawei") -> "HUAWEI_EMUI"
            manufacturer.contains("xiaomi") -> "XIAOMI_MI"
            manufacturer.contains("oppo") -> "OPPO_LINK"
            manufacturer.contains("vivo") -> "VIVO_CONNECT"
            manufacturer.contains("oneplus") -> "ONEPLUS_OXYGEN"
            manufacturer.contains("realme") -> "REALME_NODE"
            manufacturer.contains("motorola") -> "MOTOROLA_MOTO"
            manufacturer.contains("sony") -> "SONY_XPERIA"
            manufacturer.contains("jabra") -> "JABRA_SOUND"
            manufacturer.contains("bose") -> "BOSE_QUIET"
            manufacturer.contains("garmin") -> "GARMIN_TACTICAL"
            manufacturer.contains("fitbit") -> "FITBIT_HEALTH"
            manufacturer.contains("espressif") -> "ESP32_TRANSCEIVER"
            manufacturer.contains("microsoft") -> "SURFACE_HUB"
            manufacturer.contains("intel") -> "PRO_WIRELESS"
            manufacturer.contains("amazon") -> "AMAZON_ECHO"
            else -> "UNKNOWN_INTERCEPT"
        }
        
        val typeSuffix = when (result.deviceType) {
            DeviceType.SMARTPHONE -> "STATION"
            DeviceType.SMARTWATCH -> "WEARABLE"
            DeviceType.WIRELESS_EARBUD -> "AUDIO_LINK"
            DeviceType.NANO_EARPIECE -> "COVERT_TX"
            DeviceType.MAGNETIC_ANOMALY -> "FIELD_ANOMALY"
            else -> "SIGNAL_NODE"
        }

        return "$prefix$brandPrefix // $typeSuffix"
    }

    private fun calculateConfidence(type: DeviceType, detectedObject: DetectedObject, zone: DistanceZone): Int {
        var score = when (type) {
            DeviceType.SMARTWATCH, DeviceType.SMARTPHONE -> 85
            DeviceType.WIRELESS_EARBUD, DeviceType.NANO_EARPIECE -> 75
            DeviceType.MAGNETIC_ANOMALY -> 30 // Deprioritized, the Fusion Engine handles real threats
            DeviceType.ROUTER_INFRASTRUCTURE -> 10 
            DeviceType.SUSPICIOUS_UNKNOWN -> 50
        }


        if (zone == DistanceZone.IMMEDIATE) {
            score += 15
        } else if (zone == DistanceZone.NEAR) {
            score += 5
        }

        return score
    }

    private fun determineRiskLevel(type: DeviceType, zone: DistanceZone, confidence: Int, context: AnalysisContext): RiskLevel {
        if (context.isMarkedFriendly) return RiskLevel.LEVEL_1_SUSPICIOUS
        if (context.isMarkedCheating) return RiskLevel.LEVEL_4_CONFIRMED_THREAT

        return when {
            // Repeat offender logic: If we've seen this cheating pattern before (learned/history), start at Level 2
            context.feedbackRiskElevation > 0 -> RiskLevel.LEVEL_2_REPEATED
            
            type == DeviceType.MAGNETIC_ANOMALY && zone == DistanceZone.IMMEDIATE -> RiskLevel.LEVEL_4_CONFIRMED_THREAT
            type == DeviceType.NANO_EARPIECE && zone == DistanceZone.IMMEDIATE -> RiskLevel.LEVEL_4_CONFIRMED_THREAT
            confidence > 90 && (zone == DistanceZone.IMMEDIATE || zone == DistanceZone.NEAR) -> RiskLevel.LEVEL_3_PROXIMITY_MATCH
            context.isRepeatedlySeenRecently && confidence > 75 -> RiskLevel.LEVEL_2_REPEATED
            else -> RiskLevel.LEVEL_1_SUSPICIOUS
        }
    }

    private fun getDistanceZone(distance: Double): DistanceZone {
        return when {
            distance < 0 -> DistanceZone.FAR // Or unknown
            distance <= 1.0 -> DistanceZone.IMMEDIATE
            distance <= 3.0 -> DistanceZone.NEAR
            distance <= 10.0 -> DistanceZone.MEDIUM
            else -> DistanceZone.FAR
        }
    }
}
