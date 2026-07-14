package ai.vaarta

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import ai.vaarta.core.reasoning.RiskLevel
import ai.vaarta.ui.RiskRing
import ai.vaarta.ui.theme.VaartaTheme
import ai.vaarta.ui.theme.riskColor
import ai.vaarta.ui.theme.stateLabel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The floating in-call copilot (Phase 4C, ADR-0003 Phase C). A foreground service (type=microphone)
 * that owns one [CopilotSession] for the whole call and draws it over the dialer as a draggable
 * bubble that expands into a ~45%-height panel showing the live WhatsApp-style thread ([ChatThread] —
 * the exact same composable the in-app screen and history detail use, so nothing drifts).
 *
 * Why a service: the mic capture and the live socket must survive the user switching to the phone
 * app, which an Activity's lifecycle can't guarantee. Why an overlay window: it's the only compliant
 * way to show coaching *on top of* the dialer without touching the call audio (ADR-0002).
 *
 * Compose in a non-Activity window needs a lifecycle/viewmodel/savedstate owner of its own — this
 * service is that owner (the three ViewTree owners on each [ComposeView] point back here).
 *
 * Consent note (ADR-0004): stopping does NOT silently persist. The finished session is published via
 * [activeSession] so the app can offer the existing explicit "Save this call" action — auto-saving
 * would violate ADR-0004's explicit-consent-to-save stance.
 */
class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    // --- Compose-in-overlay ownership (LifecycleOwner + ViewModelStoreOwner + SavedStateRegistryOwner) ---
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    // The copilot pipeline runs on the service's own scope — cancelled on destroy, tearing down any
    // in-flight coach/ground calls. This is the SAME class the in-app ViewModel wraps (Phase 4C-1).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: CopilotSession

    private lateinit var windowManager: WindowManager
    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        session = CopilotSession(scope)
        activeSessionState.value = session
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
        }
        // Default (ACTION_START): become a foreground mic service, show the bubble, start listening.
        startAsForeground()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        showBubble()
        session.startLiveListening()
        return START_NOT_STICKY
    }

    // --- Foreground notification -------------------------------------------------------------------

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Live call protection", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while VAARTA is listening to a call to coach your replies."
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("VAARTA is protecting your call")
            .setContentText("Listening for scam signs. Tap to open.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    // --- Overlay windows (collapsed bubble <-> expanded panel) -------------------------------------

    private fun baseParams(): WindowManager.LayoutParams {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // NOT_FOCUSABLE: never steals key/IME focus from the dialer; touches inside our bounds
            // still arrive (needed for the panel's buttons and scroll).
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
    }

    private fun prepare(view: ComposeView) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    private fun showBubble() {
        if (bubbleView != null) return
        removePanel()
        val params = baseParams().apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 220
        }
        val view = ComposeView(this)
        prepare(view)
        view.setContent {
            VaartaTheme {
                val level by session.displayedLevel.collectAsState()
                val chatSize by session.chat.collectAsState()
                // Tap + drag handled in Compose (reliable for overlay ComposeViews; a plain
                // View.OnTouchListener on a ComposeView does not receive the gestures).
                BubbleContent(
                    level = level,
                    hasNew = chatSize.isNotEmpty(),
                    onTap = { expand() },
                    onDrag = { dx, dy ->
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        runCatching { windowManager.updateViewLayout(view, params) }
                    },
                )
            }
        }
        bubbleView = view
        windowManager.addView(view, params)
    }

    private fun expand() {
        removeBubble()
        if (panelView != null) return
        val params = baseParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.BOTTOM
        }
        val view = ComposeView(this)
        prepare(view)
        view.setContent {
            VaartaTheme {
                PanelContent(
                    session = session,
                    onCollapse = { showBubble() },
                    onStop = { stopEverything() },
                )
            }
        }
        panelView = view
        windowManager.addView(view, params)
    }

    private fun removeBubble() { bubbleView?.let { runCatching { windowManager.removeView(it) } }; bubbleView = null }
    private fun removePanel() { panelView?.let { runCatching { windowManager.removeView(it) } }; panelView = null }

    // --- Teardown ----------------------------------------------------------------------------------

    private fun stopEverything() {
        session.stopLiveListening()
        removeBubble()
        removePanel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        session.close()
        activeSessionState.value = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "ai.vaarta.overlay.START"
        const val ACTION_STOP = "ai.vaarta.overlay.STOP"
        private const val CHANNEL_ID = "vaarta_live"
        private const val NOTIF_ID = 1001

        /** The live copilot session while the overlay is running, so the app (single source of truth)
         *  can observe the SAME thread and offer the explicit "Save this call" action (ADR-0004).
         *  Null when no overlay session is active. */
        private val activeSessionState = MutableStateFlow<CopilotSession?>(null)
        val activeSession: StateFlow<CopilotSession?> = activeSessionState.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP))
        }
    }
}

// --- Overlay Compose content (uses the shared ChatView composables) -------------------------------

/** Collapsed bubble — the SAME [RiskRing] used everywhere else, at overlay scale (design system §1):
 *  colour alone carries risk while listening, the wave glyph marks true idle, and the ring's own
 *  shield-x takes over at SCAM_PATTERN. Tap expands; drag repositions (both via Compose gestures —
 *  reliable inside an overlay ComposeView). */
@Composable
private fun BubbleContent(level: RiskLevel, hasNew: Boolean, onTap: () -> Unit, onDrag: (Float, Float) -> Unit) {
    val c = VaartaTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(6.dp)
            .size(60.dp)
            .pointerInput(Unit) { detectTapGestures { onTap() } }
            .pointerInput(Unit) { detectDragGestures { _, drag -> onDrag(drag.x, drag.y) } },
    ) {
        RiskRing(level = level, score = 0, stateText = stateLabel(level), ringSize = 52.dp, stroke = 6.dp, showScore = false)
        if (level == RiskLevel.OBSERVING) {
            Icon(painterResource(R.drawable.ic_wave), contentDescription = null, tint = c.riskColor(level), modifier = Modifier.size(18.dp))
        }
        if (hasNew) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(12.dp)
                    .background(c.scam, CircleShape),
            )
        }
    }
}

/** Expanded ~45%-height panel: compact banner + scam-ID + the shared live thread + controls. */
@Composable
private fun PanelContent(session: CopilotSession, onCollapse: () -> Unit, onStop: () -> Unit) {
    val state by session.state.collectAsState()
    val displayedLevel by session.displayedLevel.collectAsState()
    val aiRaised by session.aiRaised.collectAsState()
    val reassure by session.reassure.collectAsState()
    val scamType by session.scamType.collectAsState()
    val scamSources by session.scamSources.collectAsState()
    val chat by session.chat.collectAsState()
    val liveStatus by session.liveStatus.collectAsState()
    val scroll = rememberScrollState()
    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp

    // Follow the newest turn as the thread grows.
    LaunchedEffect(chat.size) { scroll.animateScrollTo(scroll.maxValue) }

    val c = VaartaTheme.colors
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth().padding(6.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("VAARTA", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink)
                if (liveStatus != null) {
                    Spacer(Modifier.width(8.dp))
                    Text("● $liveStatus", color = c.indigo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCollapse) { Text("▾ Hide") }
            }

            StatusBanner(level = displayedLevel, score = state.score, reassure = reassure, aiRaised = aiRaised)

            if (scamType != null) {
                ScamIdCard(scamType = scamType!!, sources = scamSources, onOpenUrl = {})
            }

            Column(Modifier.heightIn(max = maxPanelHeight).verticalScroll(scroll)) {
                if (chat.isEmpty()) {
                    Text(
                        "Listening — I'll show what to say.",
                        fontSize = 14.sp,
                        color = c.muted,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    ChatThread(chat)
                }
            }

            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = c.scam),
            ) { Text("■  Stop protection") }
        }
    }
}
