package com.example.data.model

import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0.01f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.01f)

    companion object {
        fun fromThousandth(left: Int, top: Int, right: Int, bottom: Int): BoundingBox {
            return BoundingBox(
                left = (left / 1000f).coerceIn(0f, 1f),
                top = (top / 1000f).coerceIn(0f, 1f),
                right = (right / 1000f).coerceIn(0f, 1f),
                bottom = (bottom / 1000f).coerceIn(0f, 1f)
            )
        }
    }
}

enum class ClaimVerdict(val label: String, val shortDesc: String) {
    VERIFIED("Verified True", "Supported by primary sources and authoritative records"),
    FALSE("Debunked", "Directly contradicts verified records and primary evidence"),
    MISLEADING("Misleading", "Contains partial truths but strips vital context or distorts meaning"),
    RUMOR_MUTATION("Mutated Rumor", "Repackaged variant of an earlier viral hoax with swapped dates, figures, or names"),
    UNVERIFIED("Inconclusive", "Insufficient reliable reporting to confirm or refute at this time"),
    SATIRE("Satire / Parody", "Originally published as comedy or satire being circulated as literal truth")
}

enum class MutationType(val title: String) {
    DATE_SHIFT("Date / Timeline Shift"),
    ENTITY_SUBSTITUTION("Subject / Entity Substituted"),
    STATISTIC_EXAGGERATION("Inflated / Distorted Statistic"),
    FALSE_ATTRIBUTION("Fabricated Quote / Misattribution"),
    OUT_OF_CONTEXT("Out of Context / Recycled Event"),
    NONE("No Mutation Detected")
}

@JsonClass(generateAdapter = true)
data class SlotDifference(
    val slotName: String,
    val originalValue: String,
    val claimedValue: String,
    val explanation: String
)

@JsonClass(generateAdapter = true)
data class RumorMutation(
    val originalRumorTitle: String,
    val firstReportedDate: String?,
    val mutationType: MutationType,
    val mutationSummary: String,
    val slotsChanged: List<SlotDifference>,
    val canonicalDebunkUrl: String?
)

@JsonClass(generateAdapter = true)
data class EvidenceCitation(
    val sourceTitle: String,
    val domain: String,
    val url: String,
    val snippet: String,
    val publishedDate: String? = null,
    val reliabilityScore: Float = 0.9f,
    val isFactChecker: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ExtractedClaim(
    val id: String = UUID.randomUUID().toString(),
    val claimText: String,
    val claimType: String,
    val boundingBox: BoundingBox,
    val verdict: ClaimVerdict,
    val confidence: Float,
    val explanation: String,
    val evidenceSufficiency: String,
    val rumorMutation: RumorMutation? = null,
    val citations: List<EvidenceCitation> = emptyList(),
    val verifiedTimestamp: Long = System.currentTimeMillis(),
    val correctionText: String
)

enum class VerificationMode(val displayName: String) {
    CLOUD_ASSISTED("Cloud-Assisted (Gemini Intelligence)"),
    LOCAL_ONLY("Local-Only (On-Device Privacy Mode)")
}

data class VerificationResult(
    val sessionId: String = UUID.randomUUID().toString(),
    val capturedTimestamp: Long = System.currentTimeMillis(),
    val screenTitle: String? = null,
    val claims: List<ExtractedClaim>,
    val mode: VerificationMode,
    val processingTimeMs: Long,
    val privacySummary: String,
    val isFreshCacheHit: Boolean = false
)

data class RedactionRegion(
    val id: String = UUID.randomUUID().toString(),
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val label: String,
    val isAutoDetected: Boolean,
    val isApplied: Boolean = true
)
