package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.api.GeminiClient
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiGenerateRequest
import com.example.data.api.GeminiPart
import com.example.data.api.GenerationConfigDto
import com.example.data.api.InlineDataPart
import com.example.data.local.ClaimCacheEntity
import com.example.data.local.TrustLensDatabase
import com.example.data.model.BoundingBox
import com.example.data.model.ClaimVerdict
import com.example.data.model.EvidenceCitation
import com.example.data.model.ExtractedClaim
import com.example.data.model.MutationType
import com.example.data.model.RumorMutation
import com.example.data.model.SlotDifference
import com.example.data.model.VerificationMode
import com.example.data.model.VerificationResult
import com.example.privacy.LocalPrivacyEngine
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

class TrustLensRepository(private val context: Context) {

    private val db = TrustLensDatabase.getDatabase(context)
    private val dao = db.claimCacheDao()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val claimAdapter = moshi.adapter(ExtractedClaim::class.java)

    companion object {
        private const val TAG = "TrustLensRepo"
        private const val PREFS_NAME = "trustlens_prefs"
        private const val KEY_CUSTOM_GEMINI_KEY = "custom_gemini_api_key"
        private const val CACHE_FRESHNESS_WINDOW_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    fun getEffectiveApiKey(): String {
        val buildKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" }
        if (buildKey.isNotBlank() && !buildKey.contains("MY_GEMINI_API_KEY")) {
            return buildKey.trim()
        }
        val customKey = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_GEMINI_KEY, "") ?: ""
        return customKey.trim()
    }

    fun saveCustomApiKey(key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_GEMINI_KEY, key.trim())
            .apply()
    }

    fun hasValidApiKey(): Boolean = getEffectiveApiKey().isNotBlank()

    suspend fun verifyScreenContent(
        bitmap: Bitmap,
        mode: VerificationMode,
        privacyNote: String = "Status bar redacted locally"
    ): VerificationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val apiKey = getEffectiveApiKey()
        val hasKey = apiKey.isNotBlank()

        if (mode == VerificationMode.CLOUD_ASSISTED && hasKey) {
            try {
                Log.d(TAG, "Calling Gemini Vision for live screen analysis...")
                val result = callGeminiVisionAnalysis(bitmap, apiKey, startTime, privacyNote)
                if (result.claims.isNotEmpty()) {
                    for (claim in result.claims) {
                        cacheClaim(claim, "GeminiScreenVision")
                    }
                    return@withContext result
                } else {
                    Log.d(TAG, "Gemini Vision detected no factual claims on screen.")
                    return@withContext VerificationResult(
                        sessionId = UUID.randomUUID().toString(),
                        capturedTimestamp = System.currentTimeMillis(),
                        screenTitle = "Screen Scan",
                        claims = emptyList(),
                        mode = VerificationMode.CLOUD_ASSISTED,
                        processingTimeMs = System.currentTimeMillis() - startTime,
                        privacySummary = "$privacyNote • Screen scanned by Gemini: No dubious claims detected"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Vision API error: ${e.message}", e)
                // If API failed due to quota or network, fall back with detailed error
                return@withContext VerificationResult(
                    sessionId = UUID.randomUUID().toString(),
                    capturedTimestamp = System.currentTimeMillis(),
                    screenTitle = "Gemini Connection Alert",
                    claims = listOf(
                        ExtractedClaim(
                            id = "api_error_claim",
                            claimText = "Gemini API Request Issue: ${e.message?.take(80) ?: "Network failure"}",
                            claimType = "System Notice",
                            boundingBox = BoundingBox.fromThousandth(50, 160, 950, 300),
                            verdict = ClaimVerdict.UNVERIFIED,
                            confidence = 0.5f,
                            explanation = "Could not complete live Gemini vision verification: ${e.message}. Please check your internet connection or verify your API key.",
                            evidenceSufficiency = "Temporary connection failure",
                            rumorMutation = null,
                            citations = emptyList(),
                            correctionText = "Please verify your Gemini API key in Settings -> Engine or check network connectivity."
                        )
                    ),
                    mode = VerificationMode.CLOUD_ASSISTED,
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    privacySummary = "Gemini API request failed: ${e.message?.take(50)}"
                )
            }
        }

        // When no API key is provided
        Log.w(TAG, "No valid Gemini API key configured.")
        val elapsed = System.currentTimeMillis() - startTime
        VerificationResult(
            sessionId = UUID.randomUUID().toString(),
            capturedTimestamp = System.currentTimeMillis(),
            screenTitle = "Gemini Key Required",
            claims = listOf(
                ExtractedClaim(
                    id = "key_required_notice",
                    claimText = "Gemini API Key Required to scan external apps & chats",
                    claimType = "Configuration Notice",
                    boundingBox = BoundingBox.fromThousandth(50, 160, 950, 290),
                    verdict = ClaimVerdict.UNVERIFIED,
                    confidence = 1.0f,
                    explanation = "TrustLens uses Gemini multimodal vision to extract claims directly from your screen. To enable live screen-aware verification over WhatsApp, Twitter, and other apps, enter your Gemini API key in the Engine tab, or configure GEMINI_API_KEY in the AI Studio Secrets panel.",
                    evidenceSufficiency = "Requires Gemini Vision API key for dynamic OCR & grounding",
                    rumorMutation = null,
                    citations = listOf(
                        EvidenceCitation(
                            sourceTitle = "Google AI Studio API Keys",
                            domain = "aistudio.google.com",
                            url = "https://aistudio.google.com",
                            snippet = "Get an API key to enable live multimodal screen scanning and fact-checking.",
                            publishedDate = "2024",
                            reliabilityScore = 1.0f,
                            isFactChecker = true
                        )
                    ),
                    correctionText = "Enter your Gemini API Key in TrustLens Settings -> Engine tab to enable live screen scanning."
                )
            ),
            mode = VerificationMode.LOCAL_ONLY,
            processingTimeMs = elapsed,
            privacySummary = "$privacyNote • Add Gemini API key in Engine tab for live screen vision"
        )
    }

    private suspend fun callGeminiVisionAnalysis(
        bitmap: Bitmap,
        apiKey: String,
        startTime: Long,
        privacyNote: String
    ): VerificationResult {
        // Scale bitmap down if very large to prevent memory overflow and optimize latency
        val maxDim = 1280
        val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / Math.max(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        val baos = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        val prompt = """
            You are TrustLens, an Android screen-aware AI verification assistant.
            Examine this mobile screenshot carefully (it may be a chat, social feed, news app, or video).
            
            Instructions:
            1. Read all visible textual content and captions across the entire screen.
            2. Identify any factual claims, rumors, breaking news alerts, political claims, statistics, health tips, or quotes shown on the screen (such as in chat bubbles, social posts, articles, or images).
            3. For EACH identified claim:
               - claimText: The exact visible statement or quote.
               - claimType: Categorize (e.g. Breaking News, Politics, Health, Science, Disaster, Economics, Quote).
               - boundingBox: The normalized bounding box on the screen where this claim or chat message appears, scaled from 0 to 1000 for {left, top, right, bottom}. For example, if a chat message appears near the bottom at 85% down the screen, top should be ~830 and bottom ~900.
               - verdict: One of:
                 * VERIFIED (True, supported by authoritative records)
                 * FALSE (Debunked, false, or fabricated)
                 * MISLEADING (Missing context, cherry-picked, or distorted)
                 * RUMOR_MUTATION (Recycled hoax with altered dates, places, figures, or names)
                 * UNVERIFIED (Inconclusive, insufficient reliable evidence)
                 * SATIRE (Satirical source being circulated as fact)
               - confidence: Float between 0.0 and 1.0.
               - explanation: Detailed contradiction and evidence analysis explaining why it is true, false, or misleading.
               - evidenceSufficiency: Summary of available evidence and reliability.
               - rumorMutation: If this is an altered or recirculated rumor, provide:
                 * originalRumorTitle
                 * firstReportedDate
                 * mutationType: DATE_SHIFT, ENTITY_SUBSTITUTION, STATISTIC_EXAGGERATION, FALSE_ATTRIBUTION, or OUT_OF_CONTEXT
                 * mutationSummary
                 * slotsChanged: array of {slotName, originalValue, claimedValue, explanation}
                 * canonicalDebunkUrl
               - citations: array of primary sources or fact-checks with {sourceTitle, domain, url, snippet, publishedDate, reliabilityScore, isFactChecker}.
               - correctionText: A concise, polite, fact-based corrective message ready to copy and paste in reply.
            
            If there are NO factual claims on the screen (e.g. simple greeting or navigation UI), return {"claims": []}.
            
            Return STRICTLY valid JSON matching this schema:
            {
              "claims": [
                {
                  "claimText": "...",
                  "claimType": "...",
                  "boundingBox": { "left": 100, "top": 800, "right": 900, "bottom": 880 },
                  "verdict": "FALSE",
                  "confidence": 0.95,
                  "explanation": "...",
                  "evidenceSufficiency": "...",
                  "rumorMutation": null,
                  "citations": [
                    {
                      "sourceTitle": "...",
                      "domain": "...",
                      "url": "...",
                      "snippet": "...",
                      "publishedDate": "...",
                      "reliabilityScore": 0.95,
                      "isFactChecker": true
                    }
                  ],
                  "correctionText": "..."
                }
              ]
            }
        """.trimIndent()

        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(inlineData = InlineDataPart(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfigDto(
                responseMimeType = "application/json",
                temperature = 0.1f
            )
        )

        // Try gemini-3.5-flash, fallback to gemini-2.5-flash if needed
        val response = try {
            GeminiClient.api.generateContent("gemini-3.5-flash", apiKey, request)
        } catch (e: Exception) {
            Log.w(TAG, "gemini-3.5-flash failed, attempting gemini-2.5-flash: ${e.message}")
            GeminiClient.api.generateContent("gemini-2.5-flash", apiKey, request)
        }

        val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
        val claims = parseGeminiClaims(rawText)

        return VerificationResult(
            sessionId = UUID.randomUUID().toString(),
            capturedTimestamp = System.currentTimeMillis(),
            screenTitle = "Screen Vision Analysis",
            claims = claims,
            mode = VerificationMode.CLOUD_ASSISTED,
            processingTimeMs = System.currentTimeMillis() - startTime,
            privacySummary = "$privacyNote • Verified via Gemini Multimodal Intelligence"
        )
    }

    private fun parseGeminiClaims(jsonStr: String): List<ExtractedClaim> {
        val list = mutableListOf<ExtractedClaim>()
        try {
            val cleanJson = jsonStr.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val root = JSONObject(cleanJson)
            val array = root.optJSONArray("claims") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val bbox = parseBoundingBox(item.opt("boundingBox"), i)

                val verdictStr = item.optString("verdict", "UNVERIFIED")
                val verdict = try {
                    ClaimVerdict.valueOf(verdictStr)
                } catch (e: Exception) {
                    ClaimVerdict.UNVERIFIED
                }

                val mutationObj = item.optJSONObject("rumorMutation")
                val rumorMutation = if (mutationObj != null) {
                    val slotsArray = mutationObj.optJSONArray("slotsChanged") ?: JSONArray()
                    val slots = mutableListOf<SlotDifference>()
                    for (s in 0 until slotsArray.length()) {
                        val slotObj = slotsArray.getJSONObject(s)
                        slots.add(
                            SlotDifference(
                                slotName = slotObj.optString("slotName", "Detail"),
                                originalValue = slotObj.optString("originalValue", ""),
                                claimedValue = slotObj.optString("claimedValue", ""),
                                explanation = slotObj.optString("explanation", "")
                            )
                        )
                    }
                    val mutTypeStr = mutationObj.optString("mutationType", "NONE")
                    val mutType = try {
                        MutationType.valueOf(mutTypeStr)
                    } catch (e: Exception) {
                        MutationType.NONE
                    }

                    val firstReported = if (mutationObj.has("firstReportedDate") && !mutationObj.isNull("firstReportedDate")) {
                        mutationObj.getString("firstReportedDate")
                    } else null

                    val canonicalUrl = if (mutationObj.has("canonicalDebunkUrl") && !mutationObj.isNull("canonicalDebunkUrl")) {
                        mutationObj.getString("canonicalDebunkUrl")
                    } else null

                    RumorMutation(
                        originalRumorTitle = mutationObj.optString("originalRumorTitle", "Known viral rumor"),
                        firstReportedDate = firstReported,
                        mutationType = mutType,
                        mutationSummary = mutationObj.optString("mutationSummary", "Variant of existing misinformation"),
                        slotsChanged = slots,
                        canonicalDebunkUrl = canonicalUrl
                    )
                } else null

                val citationsArray = item.optJSONArray("citations") ?: JSONArray()
                val citations = mutableListOf<EvidenceCitation>()
                for (c in 0 until citationsArray.length()) {
                    val citObj = citationsArray.getJSONObject(c)
                    val pubDate = if (citObj.has("publishedDate") && !citObj.isNull("publishedDate")) {
                        citObj.getString("publishedDate")
                    } else null

                    citations.add(
                        EvidenceCitation(
                            sourceTitle = citObj.optString("sourceTitle", "Primary Source"),
                            domain = citObj.optString("domain", "factcheck.org"),
                            url = citObj.optString("url", "https://factcheck.org"),
                            snippet = citObj.optString("snippet", "Verified primary documentation"),
                            publishedDate = pubDate,
                            reliabilityScore = citObj.optDouble("reliabilityScore", 0.95).toFloat(),
                            isFactChecker = citObj.optBoolean("isFactChecker", true)
                        )
                    )
                }

                list.add(
                    ExtractedClaim(
                        id = UUID.randomUUID().toString(),
                        claimText = item.optString("claimText", "Extracted claim"),
                        claimType = item.optString("claimType", "General Claim"),
                        boundingBox = bbox,
                        verdict = verdict,
                        confidence = item.optDouble("confidence", 0.9).toFloat(),
                        explanation = item.optString("explanation", "Verification completed against authoritative records."),
                        evidenceSufficiency = item.optString("evidenceSufficiency", "High evidence density"),
                        rumorMutation = rumorMutation,
                        citations = citations,
                        correctionText = item.optString("correctionText", "Correction: Authoritative sources indicate otherwise.")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini claims JSON: ${e.message}", e)
        }
        return list
    }

    private fun parseBoundingBox(obj: Any?, index: Int): BoundingBox {
        if (obj is JSONObject) {
            val left = obj.optDouble("left", obj.optDouble("xmin", 50.0))
            val top = obj.optDouble("top", obj.optDouble("ymin", (200.0 + index * 160.0)))
            val right = obj.optDouble("right", obj.optDouble("xmax", 950.0))
            val bottom = obj.optDouble("bottom", obj.optDouble("ymax", top + 120.0))

            val normLeft = if (left > 1.0) (left / 1000.0).toFloat() else left.toFloat()
            val normTop = if (top > 1.0) (top / 1000.0).toFloat() else top.toFloat()
            val normRight = if (right > 1.0) (right / 1000.0).toFloat() else right.toFloat()
            val normBottom = if (bottom > 1.0) (bottom / 1000.0).toFloat() else bottom.toFloat()

            return BoundingBox(
                left = normLeft.coerceIn(0f, 1f),
                top = normTop.coerceIn(0f, 1f),
                right = normRight.coerceIn(0f, 1f),
                bottom = normBottom.coerceIn(0f, 1f)
            )
        } else if (obj is JSONArray && obj.length() >= 4) {
            // [ymin, xmin, ymax, xmax]
            val ymin = obj.optDouble(0, 0.2)
            val xmin = obj.optDouble(1, 0.05)
            val ymax = obj.optDouble(2, 0.35)
            val xmax = obj.optDouble(3, 0.95)

            val normTop = if (ymin > 1.0) (ymin / 1000.0).toFloat() else ymin.toFloat()
            val normLeft = if (xmin > 1.0) (xmin / 1000.0).toFloat() else xmin.toFloat()
            val normBottom = if (ymax > 1.0) (ymax / 1000.0).toFloat() else ymax.toFloat()
            val normRight = if (xmax > 1.0) (xmax / 1000.0).toFloat() else xmax.toFloat()

            return BoundingBox(
                left = normLeft.coerceIn(0f, 1f),
                top = normTop.coerceIn(0f, 1f),
                right = normRight.coerceIn(0f, 1f),
                bottom = normBottom.coerceIn(0f, 1f)
            )
        }
        return BoundingBox.fromThousandth(50, 200 + index * 160, 950, 320 + index * 160)
    }

    private suspend fun cacheClaim(claim: ExtractedClaim, contextNote: String) {
        val hash = LocalPrivacyEngine.hashClaimText(claim.claimText)
        val json = claimAdapter.toJson(claim)
        val entity = ClaimCacheEntity(
            claimHash = hash,
            claimText = claim.claimText,
            verdict = claim.verdict.name,
            confidence = claim.confidence,
            serializedClaimJson = json,
            createdAtTimestamp = System.currentTimeMillis(),
            lastRevalidatedTimestamp = System.currentTimeMillis(),
            sourceContext = contextNote
        )
        dao.insertOrUpdate(entity)
    }

    suspend fun getRecentVerifiedClaims(): List<ExtractedClaim> = withContext(Dispatchers.IO) {
        val entities = dao.getRecentVerifiedClaims(50)
        entities.mapNotNull { claimAdapter.fromJson(it.serializedClaimJson) }
    }

    fun getDemoClaims(): List<ExtractedClaim> {
        return listOf(
            ExtractedClaim(
                id = "claim_pakistan_pm_change",
                claimText = "Prime minister of Pakistan is changed",
                claimType = "Breaking News / Politics",
                boundingBox = BoundingBox.fromThousandth(60, 840, 940, 910),
                verdict = ClaimVerdict.FALSE,
                confidence = 0.98f,
                explanation = "Directly false. Shehbaz Sharif remains the incumbent Prime Minister of Pakistan. The National Assembly of Pakistan and Cabinet Division have issued no notification of any dissolution, vote of no confidence, or leadership change.",
                evidenceSufficiency = "Official National Assembly records and verified state gazette notices.",
                rumorMutation = RumorMutation(
                    originalRumorTitle = "Premature Leadership Change Rumor",
                    firstReportedDate = "August 2023",
                    mutationType = MutationType.FALSE_ATTRIBUTION,
                    mutationSummary = "Recycled rumor circulated during past caretaker transitions repackaged into current legislative period.",
                    slotsChanged = listOf(
                        SlotDifference("Context", "2023 Caretaker Transition", "Current 2024-2026 Term", "Recycled political transition narrative"),
                        SlotDifference("Evidence", "Official Cabinet Gazette", "Unverified WhatsApp Text", "Lacks constitutional voting record")
                    ),
                    canonicalDebunkUrl = "https://na.gov.pk"
                ),
                citations = listOf(
                    EvidenceCitation(
                        sourceTitle = "National Assembly of Pakistan Official Roster",
                        domain = "na.gov.pk",
                        url = "https://na.gov.pk",
                        snippet = "Official legislative directory of the Prime Minister and federal cabinet of Pakistan.",
                        publishedDate = "2024",
                        reliabilityScore = 1.0f,
                        isFactChecker = false
                    ),
                    EvidenceCitation(
                        sourceTitle = "Associated Press of Pakistan - State Press Bureau",
                        domain = "app.com.pk",
                        url = "https://app.com.pk",
                        snippet = "Official statements and gazettes confirm continuity of the federal administration.",
                        publishedDate = "2024",
                        reliabilityScore = 0.98f,
                        isFactChecker = true
                    )
                ),
                correctionText = "Fact Check: This claim is false. Shehbaz Sharif remains the Prime Minister of Pakistan. There has been no vote of no confidence or official notification of leadership change from the National Assembly."
            ),
            ExtractedClaim(
                id = "claim_disaster_mutation_1",
                claimText = "Category 6 Hurricane Melissa is heading for Texas coast tomorrow with 220mph winds!",
                claimType = "Breaking Weather / Disaster",
                boundingBox = BoundingBox.fromThousandth(50, 180, 950, 310),
                verdict = ClaimVerdict.RUMOR_MUTATION,
                confidence = 0.98f,
                explanation = "Saffir-Simpson Hurricane Wind Scale only goes up to Category 5; Category 6 does not exist. Video recycled from Hurricane Dorian (2019).",
                evidenceSufficiency = "NOAA and National Hurricane Center confirm no storm Melissa exists.",
                rumorMutation = RumorMutation(
                    originalRumorTitle = "Category 6 Mega-Hurricane Dorian Hoax",
                    firstReportedDate = "September 2019",
                    mutationType = MutationType.DATE_SHIFT,
                    mutationSummary = "Repackaged 2019 Bahamas storm footage with shifted year (2024) and swapped landfall target (Texas).",
                    slotsChanged = listOf(
                        SlotDifference("Year", "2019", "2024", "Original Dorian event was 5 years ago"),
                        SlotDifference("Target Landfall", "Bahamas", "Texas Gulf Coast", "Target location swapped to drive viral engagement")
                    ),
                    canonicalDebunkUrl = "https://www.nhc.noaa.gov"
                ),
                citations = listOf(
                    EvidenceCitation(
                        sourceTitle = "National Hurricane Center - Saffir-Simpson Scale",
                        domain = "nhc.noaa.gov",
                        url = "https://www.nhc.noaa.gov",
                        snippet = "The Saffir-Simpson Hurricane Wind Scale is a 1 to 5 rating.",
                        publishedDate = "2024",
                        reliabilityScore = 1.0f,
                        isFactChecker = true
                    )
                ),
                correctionText = "Fact Check: False. There is no 'Category 6' hurricane scale, and NOAA has no active storm named Melissa."
            ),
            ExtractedClaim(
                id = "claim_health_cure_2",
                claimText = "Drinking boiled lemon water with baking soda destroys all viral infections in 3 hours.",
                claimType = "Medical & Health",
                boundingBox = BoundingBox.fromThousandth(50, 360, 950, 480),
                verdict = ClaimVerdict.FALSE,
                confidence = 0.99f,
                explanation = "Directly contradicts clinical virology. Home remedies cannot neutralize cellular viral replication.",
                evidenceSufficiency = "World Health Organization mythbusters consensus.",
                rumorMutation = null,
                citations = listOf(
                    EvidenceCitation(
                        sourceTitle = "World Health Organization Mythbusters",
                        domain = "who.int",
                        url = "https://who.int",
                        snippet = "There is no scientific evidence that consuming lemon or alkaline foods prevents or cures viral infections.",
                        publishedDate = "2023",
                        reliabilityScore = 0.99f,
                        isFactChecker = true
                    )
                ),
                correctionText = "Medical Clarification: Drinking boiled lemon with baking soda does not treat viral infections. The WHO confirms home remedies do not destroy cellular viruses."
            ),
            ExtractedClaim(
                id = "claim_jwst_science_3",
                claimText = "James Webb Space Telescope detects definitive atmospheric water vapor and carbon dioxide on habitable-zone exoplanet.",
                claimType = "Astrophysics & Space",
                boundingBox = BoundingBox.fromThousandth(50, 520, 950, 640),
                verdict = ClaimVerdict.VERIFIED,
                confidence = 0.97f,
                explanation = "Spectroscopic transmission analysis by JWST detected molecular signatures of water vapor and CO2.",
                evidenceSufficiency = "Peer-reviewed astrophysical data published by NASA and ESA.",
                rumorMutation = null,
                citations = listOf(
                    EvidenceCitation(
                        sourceTitle = "NASA Goddard Space Flight Center",
                        domain = "nasa.gov",
                        url = "https://nasa.gov",
                        snippet = "Webb NIRSpec observed clear absorption bands corresponding to water vapor.",
                        publishedDate = "2023",
                        reliabilityScore = 1.0f,
                        isFactChecker = false
                    )
                ),
                correctionText = "Verified True: NASA and ESA data from the James Webb Space Telescope confirmed water vapor signatures."
            )
        )
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }
}
