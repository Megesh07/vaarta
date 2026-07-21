package ai.vaarta

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The ONE shared live-call [CopilotSession] for the whole process (redesign spec §B2). Before this,
 * the in-app live page ([SessionViewModel]) and the floating overlay ([OverlayService]) each created
 * their OWN [CopilotSession] — so minimizing to the floating bubble silently started a SECOND,
 * empty session instead of continuing the one on screen, and "restore" had no real conversation to
 * show. Whichever surface asks first creates the session; every later caller (the other surface,
 * or the same one again) gets back the exact same instance — same chat thread, same risk state,
 * same mic/socket ownership ([CopilotSession.startLiveListening] already no-ops if already live).
 *
 * Deliberately process-scoped, not tied to any Activity/Service lifecycle: the whole point is that
 * the session must survive the Activity being backgrounded (minimized) AND the Service being torn
 * down and rebuilt (bubble hidden/shown) without losing the call in progress. Neither owner calls
 * [CopilotSession.close] on its own teardown anymore — that would kill the mic/socket for whichever
 * surface is still using it; the OS reclaims everything on process death, which is an acceptable
 * bound for a single foreground app's in-memory live-call state (DATABASE_DESIGN.md §2 — RAM only).
 */
object LiveSessionHolder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var session: CopilotSession? = null

    /** Returns the one shared session, creating it on first call. Idempotent — safe to call from
     *  both [SessionViewModel] and [OverlayService], in either order, as many times as needed. */
    @Synchronized
    fun getOrCreate(appContext: Context): CopilotSession =
        session ?: CopilotSession(scope, appContext.applicationContext).also { session = it }
}
