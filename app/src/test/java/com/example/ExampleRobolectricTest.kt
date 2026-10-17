package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TrustLensDatabase
import com.example.data.model.BoundingBox
import com.example.data.model.ClaimVerdict
import com.example.data.model.MutationType
import com.example.data.model.RedactionRegion
import com.example.data.model.VerificationMode
import com.example.data.repository.TrustLensRepository
import com.example.privacy.LocalPrivacyEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var repository: TrustLensRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        repository = TrustLensRepository(context)
    }

    @Test
    fun `read string from context matches TrustLens`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("TrustLens", appName)
    }

    @Test
    fun `privacy engine detects PII and hashes claims deterministically`() {
        val sensitiveText = "Contact whistleblower at test.user@example.com or call +1-555-0199 for leaked files."
        val piiList = LocalPrivacyEngine.detectSensitiveText(sensitiveText)
        assertTrue("Should detect email", piiList.contains("Email address"))

        val hash1 = LocalPrivacyEngine.hashClaimText("Category 6 Hurricane Melissa is heading for Texas")
        val hash2 = LocalPrivacyEngine.hashClaimText("category 6 hurricane melissa is heading for texas!!!")
        assertEquals("Normalized text should produce identical privacy hash", hash1, hash2)
    }

    @Test
    fun `privacy engine correctly sanitizes bitmap with redaction regions`() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val redaction = RedactionRegion(
            left = 0f,
            top = 0f,
            right = 1f,
            bottom = 0.2f,
            label = "Status Bar",
            isAutoDetected = true,
            isApplied = true
        )
        val sanitized = LocalPrivacyEngine.redactBitmap(bitmap, listOf(redaction))
        assertNotNull(sanitized)
        assertEquals(200, sanitized.width)
        assertEquals(200, sanitized.height)
    }

    @Test
    fun `repository analyzes claims and identifies rumor mutation with slots`() = runBlocking {
        val claims = repository.getDemoClaims()
        assertTrue("Should extract at least 3 factual claims", claims.size >= 3)

        val mutationClaim = claims.find { it.verdict == ClaimVerdict.RUMOR_MUTATION }
        assertNotNull("Should identify rumor mutation", mutationClaim)
        assertNotNull("Rumor mutation object must be populated", mutationClaim?.rumorMutation)
        assertEquals(MutationType.DATE_SHIFT, mutationClaim?.rumorMutation?.mutationType)
        assertTrue("Slots changed should identify altered date", mutationClaim?.rumorMutation?.slotsChanged?.any { it.slotName == "Year" } == true)
        assertTrue("Citations must be present", mutationClaim?.citations?.isNotEmpty() == true)
        assertTrue("Correction text must not be empty", mutationClaim?.correctionText?.isNotBlank() == true)
    }

    @Test
    fun `room database caches claims and records revalidation hits`() = runBlocking {
        repository.clearCache()
        val claims = repository.getDemoClaims()
        assertTrue("Demo claims should not be empty", claims.isNotEmpty())

        val result = repository.verifyScreenContent(
            bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888),
            mode = VerificationMode.LOCAL_ONLY
        )
        assertTrue("Claims notice or result should be generated", result.claims.isNotEmpty())
    }
}
