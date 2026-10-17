package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ClaimVerdict
import com.example.data.model.ExtractedClaim
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.VerdictFalse
import com.example.ui.theme.VerdictMisleading
import com.example.ui.theme.VerdictMutation
import com.example.ui.theme.VerdictSatire
import com.example.ui.theme.VerdictUnverified
import com.example.ui.theme.VerdictVerified
import kotlin.math.roundToInt

@Composable
fun SpatialBadgeOverlay(
    claims: List<ExtractedClaim>,
    onSelectClaim: (ExtractedClaim) -> Unit,
    onDismissAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (claims.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("spatial_badge_overlay")
    ) {
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()

        // Background scrim to focus on detected claims
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable { onDismissAll() }
        )

        // Top HUD banner
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xF2090D16),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(CyanPrimary, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${claims.size} Claims Mapped • Tap any badge to inspect",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "✕ Done",
                    color = CyanPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onDismissAll() }
                        .padding(4.dp)
                )
            }
        }

        // Render each claim's bounding box and badge at exact screen coordinates
        claims.forEachIndexed { index, claim ->
            val box = claim.boundingBox
            val leftPx = box.left * containerWidthPx
            val topPx = box.top * containerHeightPx
            val boxWidthPx = (box.right - box.left) * containerWidthPx
            val boxHeightPx = (box.bottom - box.top) * containerHeightPx

            val (verdictColor, verdictIcon) = when (claim.verdict) {
                ClaimVerdict.VERIFIED -> Pair(VerdictVerified, Icons.Default.CheckCircle)
                ClaimVerdict.FALSE -> Pair(VerdictFalse, Icons.Default.Close)
                ClaimVerdict.MISLEADING -> Pair(VerdictMisleading, Icons.Default.WarningAmber)
                ClaimVerdict.RUMOR_MUTATION -> Pair(VerdictMutation, Icons.Default.Refresh)
                ClaimVerdict.UNVERIFIED -> Pair(VerdictUnverified, Icons.Default.HelpOutline)
                ClaimVerdict.SATIRE -> Pair(VerdictSatire, Icons.Default.AutoAwesome)
            }

            val density = LocalDensity.current
            val boxWidthDp = with(density) { boxWidthPx.toDp() }.coerceAtLeast(100.dp)
            val boxHeightDp = with(density) { boxHeightPx.toDp() }.coerceAtLeast(40.dp)

            // Bounding box highlight
            Box(
                modifier = Modifier
                    .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                    .size(width = boxWidthDp, height = boxHeightDp)
                    .border(
                        width = 2.dp,
                        color = verdictColor.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(verdictColor.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp))
                    .clickable { onSelectClaim(claim) }
            )

            // Anchored badge pill placed above or beside the claim box
            val badgeOffsetY = (topPx - 38f).coerceAtLeast(60f)
            val badgeOffsetX = leftPx.coerceIn(16f, containerWidthPx - 260f)

            Surface(
                modifier = Modifier
                    .offset { IntOffset(badgeOffsetX.roundToInt(), badgeOffsetY.roundToInt()) }
                    .clickable { onSelectClaim(claim) }
                    .testTag("claim_badge_${claim.id}"),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF50F172A),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, verdictColor),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = verdictIcon,
                        contentDescription = null,
                        tint = verdictColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${claim.verdict.label} (${(claim.confidence * 100).toInt()}%)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
