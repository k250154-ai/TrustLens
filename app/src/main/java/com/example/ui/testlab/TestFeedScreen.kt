package com.example.ui.testlab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ClaimVerdict
import com.example.data.model.ExtractedClaim
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.VerdictFalse
import com.example.ui.theme.VerdictMisleading
import com.example.ui.theme.VerdictMutation
import com.example.ui.theme.VerdictVerified

data class FeedPost(
    val id: String,
    val authorName: String,
    val authorHandle: String,
    val avatarColor: Color,
    val timeAgo: String,
    val text: String,
    val mediaLabel: String? = null,
    val shares: String,
    val likes: String,
    val targetClaimId: String
)

@Composable
fun TestFeedScreen(
    claims: List<ExtractedClaim>,
    isScanning: Boolean,
    activeOverlayCount: Int,
    onTriggerScan: () -> Unit,
    onClaimSelected: (ExtractedClaim) -> Unit,
    onOpenPrivacySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val samplePosts = listOf(
        FeedPost(
            id = "post_0_chat",
            authorName = "Anna (Chat Message)",
            authorHandle = "@chat_forward",
            avatarColor = Color(0xFF25D366),
            timeAgo = "12:09 PM • Viral Forward",
            text = "Prime minister of Pakistan is changed",
            mediaLabel = "💬 Viral Message Forward",
            shares = "142K",
            likes = "98K",
            targetClaimId = "claim_pakistan_pm_change"
        ),
        FeedPost(
            id = "post_1",
            authorName = "StormWatch Network",
            authorHandle = "@storm_alert_usa",
            avatarColor = Color(0xFFD32F2F),
            timeAgo = "12m ago • 45.2K views",
            text = "BREAKING ALERT: Category 6 Hurricane Melissa is heading for Texas coast tomorrow with 220mph winds! Evacuate immediately!",
            mediaLabel = "🔴 Live Radar Broadcast [Recycled 2019 Dorian Clip]",
            shares = "12.4K",
            likes = "38.1K",
            targetClaimId = "claim_disaster_mutation_1"
        ),
        FeedPost(
            id = "post_2",
            authorName = "Holistic Life Secrets",
            authorHandle = "@natural_remedies_daily",
            avatarColor = Color(0xFF388E3C),
            timeAgo = "2h ago • Viral Trend",
            text = "Big pharma doesn't want you to know this: Drinking boiled lemon water with baking soda destroys all viral infections in 3 hours! Share to save a life!",
            mediaLabel = "🍋 Natural Alkaline Miracle Guide",
            shares = "84.9K",
            likes = "112K",
            targetClaimId = "claim_health_cure_2"
        ),
        FeedPost(
            id = "post_3",
            authorName = "AstroDaily News",
            authorHandle = "@deep_space_observatory",
            avatarColor = Color(0xFF1976D2),
            timeAgo = "5h ago • Science News",
            text = "James Webb Space Telescope detects definitive atmospheric water vapor and carbon dioxide on habitable-zone exoplanet.",
            mediaLabel = "🔭 Infrared Spectrograph Transmission Spectra",
            shares = "6.1K",
            likes = "44.3K",
            targetClaimId = "claim_jwst_science_3"
        ),
        FeedPost(
            id = "post_4",
            authorName = "Wall Street Whispers",
            authorHandle = "@finance_insider_leak",
            avatarColor = Color(0xFFF57C00),
            timeAgo = "8h ago • Economy",
            text = "SHOCK REPORT: National grocery prices increased by 400% in a single month according to new federal report.",
            mediaLabel = "📈 CPI Food Price Index Chart (Distorted Scale)",
            shares = "28.5K",
            likes = "51.2K",
            targetClaimId = "claim_economic_stat_4"
        )
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("feed_list")
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                // Simulator Banner
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FilterCenterFocus,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Screen-Aware Assistant Simulator",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = CyanPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "This feed simulates viral social media and news content containing mutations, rumors, and verified science. Tap the floating TrustLens bubble to trigger screen capture and spatial badges.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            items(samplePosts) { post ->
                val associatedClaim = claims.find { it.id == post.targetClaimId }
                FeedPostCard(
                    post = post,
                    claim = associatedClaim,
                    onInspectClaim = {
                        associatedClaim?.let { onClaimSelected(it) }
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Floating Scan Bubble (In-App floating launcher)
        FloatingActionButton(
            onClick = onTriggerScan,
            containerColor = CyanPrimary,
            contentColor = Color(0xFF090D16),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .size(64.dp)
                .testTag("floating_scan_bubble")
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color(0xFF090D16),
                    strokeWidth = 3.dp
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FilterCenterFocus,
                        contentDescription = "Scan Screen with TrustLens",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FeedPostCard(
    post: FeedPost,
    claim: ExtractedClaim?,
    onInspectClaim: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Post Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(post.avatarColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = post.authorName.take(1),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${post.authorHandle} • ${post.timeAgo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Post Content Text
            Text(
                text = post.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )

            // Optional Media Thumbnail Simulation
            post.mediaLabel?.let { label ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Spatial Badge Indicator if claim was extracted & verified
            if (claim != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val (color, icon) = when (claim.verdict) {
                    ClaimVerdict.VERIFIED -> Pair(VerdictVerified, Icons.Default.CheckCircle)
                    ClaimVerdict.FALSE -> Pair(VerdictFalse, Icons.Default.Close)
                    ClaimVerdict.MISLEADING -> Pair(VerdictMisleading, Icons.Default.Warning)
                    ClaimVerdict.RUMOR_MUTATION -> Pair(VerdictMutation, Icons.Default.Refresh)
                    ClaimVerdict.UNVERIFIED -> Pair(Color.Gray, Icons.Default.Info)
                    ClaimVerdict.SATIRE -> Pair(Color(0xFF7C4DFF), Icons.Default.AutoAwesome)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = color.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, color),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onInspectClaim() }
                        .testTag("post_claim_badge_${claim.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TrustLens: ${claim.verdict.label} (${(claim.confidence * 100).toInt()}%)",
                                color = color,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Text(
                            text = "View Evidence ➔",
                            color = CyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Post Footer (shares, likes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🔁 ${post.shares} Retweets",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "❤️ ${post.likes} Likes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
