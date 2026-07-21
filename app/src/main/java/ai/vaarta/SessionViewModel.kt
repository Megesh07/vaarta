package ai.vaarta

import android.app.Application
import androidx.lifecycle.AndroidViewModel

/**
 * Thin ViewModel wrapper around the ONE shared [CopilotSession] (redesign spec §B2). The pipeline
 * itself now lives in [LiveSessionHolder], process-scoped, so the in-app live page and the floating
 * overlay ([OverlayService]) render the exact SAME session — minimizing/restoring never loses or
 * duplicates the call. This wrapper no longer owns or closes the session (it must survive this
 * ViewModel/Activity being torn down while the call continues in the background); observe its state
 * via `vm.session.<flow>` exactly as before.
 *
 * AndroidViewModel (not plain ViewModel) since Part D's speaker-attribution wiring needs a Context
 * (SpeakerEmbedder's asset manager, VaartaDatabase.get) — `getApplication()` supplies it without this
 * class taking on any Activity lifecycle dependency.
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    val session: CopilotSession get() = LiveSessionHolder.getOrCreate(getApplication())
}
