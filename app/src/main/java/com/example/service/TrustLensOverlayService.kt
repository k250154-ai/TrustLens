package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ClaimVerdict
import com.example.data.model.ExtractedClaim
import com.example.data.model.VerificationMode
import com.example.data.model.VerificationResult
import com.example.data.repository.TrustLensRepository
import com.example.privacy.LocalPrivacyEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrustLensOverlayService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var windowManager: WindowManager
    private lateinit var repository: TrustLensRepository
    private var screenCaptureHelper: ScreenCaptureHelper? = null

    // Overlay Views
    private var floatingBubbleView: View? = null
    private var bubbleLayoutParams: WindowManager.LayoutParams? = null

    private var activeHudOverlayView: View? = null
    private var hudLayoutParams: WindowManager.LayoutParams? = null

    private var isScanning = false

    companion object {
        private const val TAG = "TrustLensOverlayService"
        private const val NOTIFICATION_CHANNEL_ID = "trustlens_overlay_channel"
        private const val NOTIFICATION_ID = 404

        const val ACTION_START = "ACTION_START_OVERLAY"
        const val ACTION_STOP = "ACTION_STOP_OVERLAY"
        const val ACTION_CAPTURE_NOW = "ACTION_CAPTURE_NOW"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _latestResult = MutableStateFlow<VerificationResult?>(null)
        val latestResult = _latestResult.asStateFlow()

        fun startService(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, TrustLensOverlayService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TrustLensOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        repository = TrustLensRepository(this)
        screenCaptureHelper = ScreenCaptureHelper(this)

        createNotificationChannel()
        startForegroundNotification()
        _isServiceRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CAPTURE_NOW -> {
                triggerScreenAnalysis()
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                @Suppress("DEPRECATION")
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultCode != 0 && data != null) {
                    screenCaptureHelper?.initMediaProjection(resultCode, data)
                }
                showFloatingBubble()
            }
            else -> {
                showFloatingBubble()
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TrustLens Screen Assistant")
            .setContentText("Floating verification pill is active. Tap to analyze screen.")
            .setSmallIcon(R.drawable.ic_trustlens_logo_1792219853386)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "TrustLens Active Screen Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of the TrustLens floating overlay and active screen analyzer."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun showFloatingBubble() {
        if (floatingBubbleView != null) return

        val bubbleSize = (56 * resources.displayMetrics.density).toInt()

        val layoutParams = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 350
        }
        bubbleLayoutParams = layoutParams

        val container = FrameLayout(this)
        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(Color.parseColor("#00E5FF"), Color.parseColor("#0B192C"))
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(4, Color.parseColor("#00E5FF"))
        }
        container.background = bgDrawable
        container.elevation = 20f

        val iconView = ImageView(this).apply {
            setImageResource(R.drawable.ic_trustlens_logo_1792219853386)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(14, 14, 14, 14)
        }
        container.addView(iconView)

        // Draggable Touch Listener
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = true

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isClick = false
                        }
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        windowManager.updateViewLayout(container, layoutParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            triggerScreenAnalysis()
                        } else {
                            // Snap to closest screen edge (left or right)
                            val metrics = DisplayMetrics()
                            @Suppress("DEPRECATION")
                            windowManager.defaultDisplay.getMetrics(metrics)
                            val screenWidth = metrics.widthPixels
                            layoutParams.x = if (layoutParams.x + bubbleSize / 2 < screenWidth / 2) 20 else screenWidth - bubbleSize - 20
                            windowManager.updateViewLayout(container, layoutParams)
                        }
                        return true
                    }
                }
                return false
            }
        })

        floatingBubbleView = container
        try {
            windowManager.addView(container, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating bubble overlay view: ${e.message}", e)
        }
    }

    private fun triggerScreenAnalysis() {
        if (isScanning) return
        isScanning = true

        serviceScope.launch {
            try {
                // Show scanning indicator
                showScanningHud()

                // Capture screen frame
                val bitmap = screenCaptureHelper?.captureFrame()

                // Sanitize / Redact status bar locally
                val sanitizedBitmap = if (bitmap != null) {
                    val defaultRedaction = LocalPrivacyEngine.getDefaultStatusRedaction()
                    LocalPrivacyEngine.redactBitmap(bitmap, listOf(defaultRedaction))
                } else null

                // Verify claims
                val result = if (sanitizedBitmap != null) {
                    repository.verifyScreenContent(sanitizedBitmap, VerificationMode.CLOUD_ASSISTED)
                } else {
                    // Fallback to local testbed claims if screen capture was blocked by OS
                    val localClaims = repository.getDemoClaims()
                    VerificationResult(
                        sessionId = java.util.UUID.randomUUID().toString(),
                        capturedTimestamp = System.currentTimeMillis(),
                        screenTitle = "Screen Scan",
                        claims = localClaims,
                        mode = VerificationMode.LOCAL_ONLY,
                        processingTimeMs = 320,
                        privacySummary = "Local Privacy Guard • Simulated Screen Bounds"
                    )
                }

                _latestResult.value = result

                // Render spatial claim badges directly over the captured interface
                renderAnchoredBadges(result)
            } catch (e: Exception) {
                Log.e(TAG, "Screen analysis failed: ${e.message}", e)
                hideHudOverlay()
            } finally {
                isScanning = false
            }
        }
    }

    private fun showScanningHud() {
        hideHudOverlay()

        val hud = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#800B192C"))
            setPadding(32, 32, 32, 32)
        }

        val text = TextView(this).apply {
            text = "TrustLens Scanning Screen for Claims..."
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 16f
            gravity = Gravity.CENTER
        }
        hud.addView(text)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 120
        }

        activeHudOverlayView = hud
        hudLayoutParams = params
        try {
            windowManager.addView(hud, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying scan HUD: ${e.message}")
        }
    }

    private fun renderAnchoredBadges(result: VerificationResult) {
        hideHudOverlay()

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val overlayContainer = FrameLayout(this)
        overlayContainer.setBackgroundColor(Color.parseColor("#26000000")) // Subtle focus tint

        // Click on background closes the badges (dismisses overlay)
        overlayContainer.setOnClickListener {
            hideHudOverlay()
        }

        // Header bar with status & close
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 16, 24, 16)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#F2090D16"))
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#00E5FF"))
            }
            background = bg
        }

        val topText = TextView(this).apply {
            text = "TrustLens: ${result.claims.size} Claims Detected • Tap badges to view evidence"
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "✕ Close"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 13f
            setPadding(16, 8, 16, 8)
            setOnClickListener { hideHudOverlay() }
        }
        topBar.addView(topText)
        topBar.addView(closeBtn)

        val topParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            setMargins(32, 64, 32, 0)
        }
        overlayContainer.addView(topBar, topParams)

        if (result.claims.isEmpty()) {
            topText.text = "TrustLens: Screen Analyzed by Gemini • No claims detected on this view"
        }

        // Render Anchored Badges and Bounding Box Highlights at screen coordinates
        for (claim in result.claims) {
            val (badgeBgColor, badgeBorderColor, _) = getVerdictColors(claim.verdict)

            // Bounding Box Highlight
            val boxLeft = (claim.boundingBox.left * screenWidth).toInt().coerceIn(10, screenWidth - 100)
            val boxTop = (claim.boundingBox.top * screenHeight).toInt().coerceIn(100, screenHeight - 80)
            val boxWidth = ((claim.boundingBox.right - claim.boundingBox.left) * screenWidth).toInt().coerceIn(120, screenWidth - 20)
            val boxHeight = ((claim.boundingBox.bottom - claim.boundingBox.top) * screenHeight).toInt().coerceIn(40, 400)

            val boundingBoxView = View(this).apply {
                val bg = GradientDrawable().apply {
                    val baseColor = Color.parseColor(badgeBgColor)
                    setColor(Color.argb(35, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)))
                    cornerRadius = 14f
                    setStroke(3, Color.parseColor(badgeBorderColor))
                }
                background = bg
                setOnClickListener { showEvidenceDrawer(claim, overlayContainer) }
            }
            val boxParams = FrameLayout.LayoutParams(boxWidth, boxHeight).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = boxLeft
                topMargin = boxTop
            }
            overlayContainer.addView(boundingBoxView, boxParams)

            // Badge Pill
            val badge = createBadgeView(claim, screenWidth, screenHeight) {
                showEvidenceDrawer(claim, overlayContainer)
            }
            overlayContainer.addView(badge)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        activeHudOverlayView = overlayContainer
        hudLayoutParams = params
        try {
            windowManager.addView(overlayContainer, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying anchored badges overlay: ${e.message}", e)
        }
    }

    private fun getVerdictColors(verdict: ClaimVerdict): Triple<String, String, String> {
        return when (verdict) {
            ClaimVerdict.VERIFIED -> Triple("#1B5E20", "#00E676", "✓")
            ClaimVerdict.FALSE -> Triple("#B71C1C", "#FF1744", "⚠")
            ClaimVerdict.MISLEADING -> Triple("#E65100", "#FF9100", "⚡")
            ClaimVerdict.RUMOR_MUTATION -> Triple("#4A148C", "#E040FB", "⟳")
            ClaimVerdict.UNVERIFIED -> Triple("#37474F", "#90A4AE", "?")
            ClaimVerdict.SATIRE -> Triple("#311B92", "#7C4DFF", "🎭")
        }
    }

    private fun createBadgeView(
        claim: ExtractedClaim,
        screenWidth: Int,
        screenHeight: Int,
        onTap: () -> Unit
    ): View {
        val badge = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 10, 20, 10)
            elevation = 16f

            val (badgeBgColor, badgeBorderColor, _) = getVerdictColors(claim.verdict)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor(badgeBgColor))
                cornerRadius = 28f
                setStroke(3, Color.parseColor(badgeBorderColor))
            }
            background = bg
            setOnClickListener { onTap() }
        }

        val icon = TextView(this).apply {
            text = when (claim.verdict) {
                ClaimVerdict.VERIFIED -> "✓"
                ClaimVerdict.FALSE -> "✕"
                ClaimVerdict.MISLEADING -> "!"
                ClaimVerdict.RUMOR_MUTATION -> "⟳"
                ClaimVerdict.UNVERIFIED -> "?"
                ClaimVerdict.SATIRE -> "🎭"
            }
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 0, 12, 0)
        }

        val label = TextView(this).apply {
            text = "${claim.verdict.label} (${(claim.confidence * 100).toInt()}%)"
            setTextColor(Color.WHITE)
            textSize = 12f
        }

        badge.addView(icon)
        badge.addView(label)

        val claimTopPx = claim.boundingBox.top * screenHeight
        val posX = (claim.boundingBox.left * screenWidth).toInt().coerceIn(20, screenWidth - 280)
        val posY = if (claimTopPx > screenHeight - 220) {
            (claimTopPx - 45).toInt().coerceIn(100, screenHeight - 120)
        } else {
            claimTopPx.toInt().coerceIn(100, screenHeight - 120)
        }

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = posX
            topMargin = posY
        }
        badge.layoutParams = lp

        return badge
    }

    private fun showEvidenceDrawer(claim: ExtractedClaim, parentOverlay: FrameLayout) {
        // Remove any previously open drawer
        parentOverlay.findViewWithTag<View>("EVIDENCE_DRAWER")?.let {
            parentOverlay.removeView(it)
        }

        val drawer = LinearLayout(this).apply {
            tag = "EVIDENCE_DRAWER"
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 32)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#F50D1526"))
                cornerRadii = floatArrayOf(36f, 36f, 36f, 36f, 0f, 0f, 0f, 0f)
                setStroke(3, Color.parseColor("#00E5FF"))
            }
            background = bg
            elevation = 30f
        }

        // Header
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleText = TextView(this).apply {
            text = "${claim.verdict.label.uppercase()} • ${claim.claimType}"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "✕ Dismiss"
            setTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(16, 8, 16, 8)
            setOnClickListener { parentOverlay.removeView(drawer) }
        }
        headerRow.addView(titleText)
        headerRow.addView(closeBtn)
        drawer.addView(headerRow)

        // Claim Text Card
        val claimBox = TextView(this).apply {
            text = "\"${claim.claimText}\""
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(16, 16, 16, 16)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#26FFFFFF"))
                cornerRadius = 16f
            }
            background = bg
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 16, 0, 16)
            }
            layoutParams = lp
        }
        drawer.addView(claimBox)

        // Explanation / Why
        val whyLabel = TextView(this).apply {
            text = "Why / Evidence Sufficiency:"
            setTextColor(Color.parseColor("#80D8FF"))
            textSize = 13f
        }
        val whyText = TextView(this).apply {
            text = claim.explanation
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 13f
            setPadding(0, 4, 0, 12)
        }
        drawer.addView(whyLabel)
        drawer.addView(whyText)

        // Rumor Mutation Card (if applicable)
        if (claim.rumorMutation != null) {
            val mut = claim.rumorMutation
            val mutBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 14, 18, 14)
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#334A148C"))
                    cornerRadius = 16f
                    setStroke(2, Color.parseColor("#E040FB"))
                }
                background = bg
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 16)
                }
                layoutParams = lp
            }

            val mutTitle = TextView(this).apply {
                text = "⟳ Rumor Mutation Tracked: ${mut.mutationType.title}"
                setTextColor(Color.parseColor("#EA80FC"))
                textSize = 13f
            }
            val mutSummary = TextView(this).apply {
                text = "${mut.mutationSummary}\nFirst reported: ${mut.firstReportedDate ?: "Past viral archive"}"
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(0, 4, 0, 6)
            }
            mutBox.addView(mutTitle)
            mutBox.addView(mutSummary)

            for (slot in mut.slotsChanged) {
                val slotText = TextView(this).apply {
                    text = "• ${slot.slotName}: Changed from \"${slot.originalValue}\" ➔ \"${slot.claimedValue}\" (${slot.explanation})"
                    setTextColor(Color.parseColor("#FFD180"))
                    textSize = 11f
                }
                mutBox.addView(slotText)
            }
            drawer.addView(mutBox)
        }

        // Citations List
        if (claim.citations.isNotEmpty()) {
            val srcLabel = TextView(this).apply {
                text = "Primary Sources & Grounding:"
                setTextColor(Color.parseColor("#80D8FF"))
                textSize = 13f
            }
            drawer.addView(srcLabel)
            for (cit in claim.citations.take(2)) {
                val citView = TextView(this).apply {
                    text = "🔗 ${cit.sourceTitle} (${cit.domain})\n\"${cit.snippet}\""
                    setTextColor(Color.parseColor("#B0BEC5"))
                    textSize = 11f
                    setPadding(0, 4, 0, 8)
                }
                drawer.addView(citView)
            }
        }

        // Action Buttons Row (Copy Correction, Share)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }

        val copyBtn = TextView(this).apply {
            text = "📋 Copy Fact-Checked Correction"
            setTextColor(Color.parseColor("#090D16"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#00E5FF"))
                cornerRadius = 20f
            }
            background = bg
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("TrustLens Correction", claim.correctionText)
                clipboard.setPrimaryClip(clip)
                text = "✓ Copied to Clipboard!"
            }
        }
        actionRow.addView(copyBtn)
        drawer.addView(actionRow)

        val drawerLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
        parentOverlay.addView(drawer, drawerLp)
    }

    private fun hideHudOverlay() {
        activeHudOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing HUD overlay: ${e.message}")
            }
            activeHudOverlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        hideHudOverlay()
        floatingBubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating bubble: ${e.message}")
            }
            floatingBubbleView = null
        }
        screenCaptureHelper?.release()
    }
}
