package com.example.betterswipekeyboard

import android.inputmethodservice.InputMethodService
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.lifecycleScope
import com.example.betterswipekeyboard.ime.bottomClearancePx
import com.example.betterswipekeyboard.emoji.EmojiSuggester
import com.example.betterswipekeyboard.layout.LayoutId
import com.example.betterswipekeyboard.proofread.MlKitProofreader
import com.example.betterswipekeyboard.proofread.OpenRouterProofreader
import com.example.betterswipekeyboard.proofread.ProofreadMode
import com.example.betterswipekeyboard.proofread.ProofreadPrompt
import com.example.betterswipekeyboard.proofread.Proofreader
import com.example.betterswipekeyboard.proofread.ProofreaderBackend
import com.example.betterswipekeyboard.proofread.ProofreaderStatus
import com.example.betterswipekeyboard.proofread.SentenceExtractor
import com.example.betterswipekeyboard.proofread.SwipedWordLog
import com.example.betterswipekeyboard.proofread.selectBackend
import com.example.betterswipekeyboard.swipe.Dictionary
import com.example.betterswipekeyboard.swipe.SwipeDecoder
import com.example.betterswipekeyboard.swipe.SwipeTrailCapture
import com.example.betterswipekeyboard.ui.keyboard.KeyboardScreen
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hosts the Compose keyboard. An InputMethodService is not an Activity, so it
 * must act as its own [LifecycleOwner]/[ViewModelStoreOwner]/
 * [SavedStateRegistryOwner] and attach those owners to the keyboard window's
 * decor view for Compose to function.
 */
class SwipeKeyboardService : InputMethodService(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var viewModel: KeyboardViewModel
    private lateinit var baseDictionary: Dictionary
    private lateinit var customWordStore: CustomWordStore
    private lateinit var decoder: SwipeDecoder
    private lateinit var emojiSuggester: EmojiSuggester
    private var lastCustomWordsRaw: String? = null
    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var mlKitProofreader: MlKitProofreader
    private lateinit var openRouterProofreader: OpenRouterProofreader

    /** The backend currently backing the sparkly button, or null when none. */
    private var activeProofreader: Proofreader? = null

    private val editor = InputConnectionEditor { currentInputConnection }
    private var autoProofreadJob: Job? = null
    private var lastCommitWasSwipe = false

    /** Crossed-letter memory for the proofreader (see SwipedWordLog). */
    private val swipedWordLog = SwipedWordLog()

    private lateinit var clipboardManager: ClipboardManager

    /**
     * Records clipboard history Gboard-style: Android keeps no history, so
     * the keyboard observes every clip while it is the selected IME (the
     * platform grants the default IME clipboard access even when the
     * keyboard is hidden). Sensitive clips (password managers, password
     * fields) are never stored.
     */
    private val clipChangedListener =
        ClipboardManager.OnPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip ?: return@OnPrimaryClipChangedListener
            val sensitive = clip.description.extras
                ?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
            if (sensitive) return@OnPrimaryClipChangedListener
            // Text clips only: no coerceToText, which could resolve URIs
            // through our own content resolver into unexpected data.
            val text = clip.getItemAt(0).text ?: return@OnPrimaryClipChangedListener
            viewModel.addClip(text.toString())
        }

    /** True when text was committed/deleted since the last proofread attempt. */
    private var textDirtySinceProofread = false

    /**
     * True between GestureStarted and GestureEnded. While a gesture runs,
     * typed-mode proofread scheduling is deferred to GestureEnded — a held
     * backspace would otherwise cancel + relaunch the timer job on every
     * repeat step.
     */
    private var gestureActive = false

    // Created lazily on first mic tap (must be created/destroyed on the main
    // thread — the service's callbacks already are).
    private var speechRecognizer: SpeechRecognizer? = null

    private val recognizerIntent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Device locale only; a language picker is a future enhancement.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            bestTranscript(partialResults?.recognitionResults())
                ?.let(viewModel::setVoicePartial)
        }

        override fun onResults(results: Bundle?) {
            viewModel.setVoiceState(VoiceState.OFF)
            commitDictation(bestTranscript(results?.recognitionResults()))
        }

        override fun onError(error: Int) {
            when (error) {
                // Our own cancel() — nothing to report.
                SpeechRecognizer.ERROR_CLIENT -> Unit
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    viewModel.setVoiceState(VoiceState.PERMISSION_REQUIRED)
                    return
                }
                // The user said nothing recognizable — fail quietly.
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Unit
                else -> android.util.Log.w("SwipeKeyboard", "speech recognition error $error")
            }
            viewModel.setVoiceState(VoiceState.OFF)
        }
    }

    /**
     * Real bottom inset (px) reported to the IME window: the height of the
     * system navigation / IME strip the keyboard must stay above. Zero when
     * no strip is present. Read by the Compose content.
     */
    private var bottomInsetPx by mutableStateOf(0)

    override fun onCreate() {
        super.onCreate()
        // Take explicit control of window insets: the IME's SoftInputWindow
        // does not reliably pad for the navigation/IME strip by itself.
        window?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        viewModel = ViewModelProvider(this)[KeyboardViewModel::class.java]
        baseDictionary = Dictionary.load(assets.open("words_en.txt"))
        emojiSuggester = EmojiSuggester.load(assets.open("emoji_keywords_en.txt"))
        customWordStore = CustomWordStore(this)
        rebuildDecoder()
        apiKeyStore = ApiKeyStore(this)
        mlKitProofreader = MlKitProofreader(this)
        openRouterProofreader = OpenRouterProofreader(apiKeyStore)
        clipboardManager = getSystemService(ClipboardManager::class.java)
        clipboardManager.addPrimaryClipChangedListener(clipChangedListener)
        SwipeTrailCapture.init(filesDir, getExternalFilesDir(null))
        lifecycleScope.launch { refreshProofreader() }
    }

    /**
     * Rebuilds the swipe decoder from the base asset dictionary merged with
     * the user's stored custom words (see [CustomWordStore]).
     */
    private fun rebuildDecoder() {
        lastCustomWordsRaw = customWordStore.rawWords
        decoder = SwipeDecoder(baseDictionary.withCustomWords(customWordStore.load()))
    }

    /**
     * On-device Gemini Nano wins when available; otherwise fall back to the
     * OpenRouter cloud backend when the user has configured an API key.
     */
    private suspend fun refreshProofreader() {
        val mlKitStatus = mlKitProofreader.status()
        val backend = selectBackend(mlKitStatus, hasApiKey = apiKeyStore.apiKey != null)
        activeProofreader = when (backend) {
            ProofreaderBackend.ON_DEVICE -> mlKitProofreader
            ProofreaderBackend.CLOUD -> openRouterProofreader
            ProofreaderBackend.NONE -> null
        }
        val status = when (backend) {
            ProofreaderBackend.NONE ->
                if (mlKitStatus == ProofreaderStatus.DOWNLOADING) {
                    ProofreaderStatus.DOWNLOADING
                } else {
                    ProofreaderStatus.UNAVAILABLE
                }
            else -> ProofreaderStatus.AVAILABLE
        }
        viewModel.setProofreaderStatus(status, backend)
    }

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
            // Measure the real occlusion at the window root: gesture strip,
            // 3-button bar or One UI's IME strip, whichever is tallest. The
            // listener MUST live on the decor view — the IME window does not
            // dispatch WindowInsets down to the input view, so a listener on
            // the ComposeView never fires (bottomInsetPx stayed 0 forever).
            ViewCompat.setOnApplyWindowInsetsListener(decor) { _, insets ->
                bottomInsetPx = bottomClearancePx(
                    insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
                    insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom,
                    insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom,
                )
                insets
            }
            ViewCompat.requestApplyInsets(decor)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return ComposeView(this).apply {
            setContent {
                val state by viewModel.state.collectAsState()
                KeyboardScreen(
                    state = state,
                    decoderProvider = { decoder },
                    onAction = ::onKeyboardAction,
                    onSettingsClick = ::openMainApp,
                    onPermissionHelpClick = ::openMainApp,
                    bottomClearance = with(LocalDensity.current) { bottomInsetPx.toDp() },
                    onSwipeDecoded = SwipeTrailCapture::record,
                )
            }
        }
    }

    private fun openMainApp() {
        packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let(::startActivity)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Pick up custom words saved in MainActivity: rebuild the decoder
        // only when the stored word string actually changed. Words saved
        // while the keyboard is open go live on the next keyboard show.
        if (customWordStore.rawWords != lastCustomWordsRaw) rebuildDecoder()
        // Pick up the debug trail-capture toggle from MainActivity.
        SwipeTrailCapture.enabled = getSharedPreferences(
            SwipeTrailCapture.PREFS_NAME, MODE_PRIVATE,
        ).getBoolean(SwipeTrailCapture.KEY_ENABLED, false)
        // Keep the status bar fresh: the model may have finished downloading
        // or the API key may have changed since the last check.
        lifecycleScope.launch { refreshProofreader() }
    }

    /**
     * The default implementation hides the soft keyboard when a hardware
     * keyboard is attached (e.g. on emulators). We always want to show.
     * Deliberately no super call: the super implementation is exactly the
     * behavior being overridden.
     */
    @android.annotation.SuppressLint("MissingSuperCall")
    override fun onEvaluateInputViewShown(): Boolean = true

    private fun onKeyboardAction(action: KeyboardAction) {
        // A touch suspends the auto-proofread timer for the gesture's whole
        // duration (a swipe or long-press produces no actions until
        // finger-up, so per-effect scheduling alone can fire mid-gesture).
        // Finger-up restarts the timer only when there is un-proofread
        // text — no wasted API calls after no-op gestures.
        if (action is KeyboardAction.GestureStarted) {
            gestureActive = true
            autoProofreadJob?.cancel()
        }
        if (action is KeyboardAction.GestureEnded) {
            gestureActive = false
            if (textDirtySinceProofread) scheduleAutoProofread()
        }
        // Voice start/stop is decided here (permission + availability checks
        // are Android concerns); the ViewModel just records the outcome.
        if (action is KeyboardAction.ToggleVoice) {
            onToggleVoice()
            return
        }
        val effect = viewModel.onAction(action)
        when (effect) {
            is KeyboardEffect.CommitText -> {
                // Mode change swipe → tap starts a new word (letters only).
                if (lastCommitWasSwipe &&
                    InputConnectionEditor.needsSpaceAfterSwipe(
                        editor.textBeforeCursor(maxChars = 1),
                        effect.text,
                    )
                ) {
                    editor.commitText(" " + effect.text)
                } else {
                    editor.commitText(effect.text)
                }
                lastCommitWasSwipe = false
                textDirtySinceProofread = true
                scheduleAutoProofread()
            }
            is KeyboardEffect.CommitWord -> {
                // commitWord inserts the leading space for tap → swipe itself.
                editor.commitWord(effect.word)
                effect.crossedLetters?.let { swipedWordLog.record(effect.word, it) }
                lastCommitWasSwipe = true
                textDirtySinceProofread = true
                scheduleAutoProofread()
            }
            is KeyboardEffect.PasteText -> {
                // Verbatim paste: no leading-space rules, and deliberately no
                // proofread scheduling — the user wants exactly what they
                // copied, not an AI-rewritten version of it.
                editor.commitText(effect.text)
                lastCommitWasSwipe = false
            }
            KeyboardEffect.DeleteBackward -> {
                editor.backspace()
                textDirtySinceProofread = true
                scheduleAutoProofread()
            }
            KeyboardEffect.DeleteWordBackward -> {
                // First backspace after a swipe: the whole word goes. The
                // service's own lastCommitWasSwipe (tap-after-swipe
                // leading-space rule) is deliberately left alone — typing
                // or swiping a replacement word still earns its space.
                editor.deleteWordBackward()
                textDirtySinceProofread = true
                scheduleAutoProofread()
            }
            KeyboardEffect.PerformEnter -> {
                editor.enter(currentInputEditorInfo)
                textDirtySinceProofread = true
                scheduleAutoProofread()
            }
            is KeyboardEffect.MoveCursor -> {
                editor.moveCursor(effect.steps)
                // Cursor move ends the swipe → tap leading-space context;
                // no scheduleAutoProofread: the text did not change.
                lastCommitWasSwipe = false
            }
            null -> Unit
        }
        // Emoji-panel suggestions: refresh on opening the panel and after
        // any text change (emoji insert, backspace, ...) while it is open;
        // clear when switching away so stale suggestions never linger.
        if (action is KeyboardAction.SwitchLayout) {
            if (action.layout == LayoutId.EMOJI) {
                refreshEmojiSuggestions()
            } else {
                viewModel.setEmojiSuggestions(emptyList())
            }
        } else if (effect != null && viewModel.state.value.layout == LayoutId.EMOJI) {
            refreshEmojiSuggestions()
        }
    }

    /**
     * Recomputes the emoji suggestion row from the text before the cursor.
     * Pure in-memory keyword lookups on the last few words — instant and
     * offline, so no debounce is needed.
     */
    private fun refreshEmojiSuggestions() {
        val before = editor.textBeforeCursor(maxChars = EMOJI_SUGGESTION_CHARS).orEmpty()
        viewModel.setEmojiSuggestions(emojiSuggester.suggest(before))
    }

    /**
     * Auto-proofreading debounce: every text change restarts a 2-second
     * timer; the proofread fires only after two full seconds of no touches or
     * text changes (so at most once per two seconds, never mid-gesture). Dictated
     * text schedules with [ProofreadMode.VOICE] so the proofreader targets
     * speech-recognition errors; the debounce is shared, so typing right
     * after dictating reschedules (last writer wins).
     */
    private fun scheduleAutoProofread(mode: ProofreadMode = ProofreadMode.TYPED) {
        if (!viewModel.state.value.proofreadAuto) return
        // Typed-mode scheduling during a gesture is deferred: GestureEnded
        // schedules when the dirty flag is set. Without this, a held
        // backspace cancels + relaunches the job on every repeat step.
        // Voice commits run outside gestures and schedule immediately.
        if (gestureActive && mode == ProofreadMode.TYPED) return
        autoProofreadJob?.cancel()
        autoProofreadJob = lifecycleScope.launch {
            delay(AUTO_PROOFREAD_DEBOUNCE_MS)
            // One attempt per dirty period; the next text change re-dirties.
            textDirtySinceProofread = false
            runProofread(mode)
        }
    }

    /**
     * Fixes the current sentence with the active proofreader (on-device when
     * possible, OpenRouter cloud otherwise). Failures are logged and
     * otherwise ignored — the keyboard must never depend on the AI.
     */
    private fun runProofread(mode: ProofreadMode) {
        val proofreader = activeProofreader ?: return
        if (viewModel.state.value.proofreadInFlight) return
        viewModel.setProofreadInFlight(true)
        lifecycleScope.launch {
            try {
                val before = editor.textBeforeCursor().orEmpty()
                // The window spans the current fragment plus the previous
                // sentence, so a continuation fragment ("and bought ...")
                // can be merged back into a sentence an earlier pass
                // terminated during a mid-thought pause.
                val window = SentenceExtractor.currentWindow(before)
                if (window.text.isBlank()) return@launch
                // Swipe-path context for the cloud typed prompt only: the
                // on-device API takes plain text, and voice requests use
                // the separate voice prompt.
                val input = if (proofreader is OpenRouterProofreader && mode != ProofreadMode.VOICE) {
                    val windowStart = before.length - window.text.length
                    val paths = swipedWordLog.reconcile(before)
                        .filter { it.startIndex >= windowStart }
                        .map { it.entry.word to it.entry.letters }
                    ProofreadPrompt.withSwipePaths(window.text.trim(), paths)
                } else {
                    window.text.trim()
                }
                val corrected = proofreader.proofread(input, mode)
                // The user may have kept typing while the request was in
                // flight; never clobber newer text. Comparing whole windows
                // also invalidates the result when either of the two
                // visible sentences changed. An echoed annotation block
                // discards the result (fail soft) — it must never land in
                // the text field.
                val latest = SentenceExtractor.currentWindow(
                    editor.textBeforeCursor().orEmpty(),
                )
                if (latest == window &&
                    corrected.isNotBlank() &&
                    corrected != window.text.trim() &&
                    !ProofreadPrompt.containsSwipePathsMarker(corrected)
                ) {
                    // Preserve the fragment's surrounding whitespace.
                    editor.replaceBeforeCursor(
                        window.text.length,
                        window.text.replace(window.text.trim(), corrected),
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("SwipeKeyboard", "proofread failed", e)
            } finally {
                viewModel.setProofreadInFlight(false)
            }
        }
    }

    /** Mic key handling, per the voice-state transition table. */
    private fun onToggleVoice() {
        when (viewModel.state.value.voice) {
            VoiceState.OFF -> startVoiceInput()
            // stopListening() finalizes; the state flips to OFF when
            // onResults/onError arrives.
            VoiceState.LISTENING -> speechRecognizer?.stopListening()
            // The panel acts as its own dismiss in the message states.
            VoiceState.PERMISSION_REQUIRED,
            VoiceState.UNAVAILABLE -> viewModel.setVoiceState(VoiceState.OFF)
        }
    }

    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Runtime permissions can only be requested from an Activity;
            // the panel routes the user to MainActivity to grant it.
            viewModel.setVoiceState(VoiceState.PERMISSION_REQUIRED)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            viewModel.setVoiceState(VoiceState.UNAVAILABLE)
            return
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(recognitionListener)
            }
        }
        viewModel.setVoiceState(VoiceState.LISTENING)
        speechRecognizer?.startListening(recognizerIntent)
    }

    /**
     * Commits the final transcript through the same path as a swiped word:
     * leading-space handling and the "tap earns a space" rule come free, and
     * the sentence is voice-proofread after the usual 2s debounce.
     */
    private fun commitDictation(transcript: String?) {
        if (transcript == null) return
        editor.commitWord(transcript)
        lastCommitWasSwipe = true
        scheduleAutoProofread(ProofreadMode.VOICE)
    }

    /** Never hold the mic after the keyboard disappears. */
    private fun cancelVoiceInput() {
        if (viewModel.state.value.voice == VoiceState.LISTENING) {
            speechRecognizer?.cancel()
        }
        if (viewModel.state.value.voice != VoiceState.OFF) {
            viewModel.setVoiceState(VoiceState.OFF)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        cancelVoiceInput()
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        cancelVoiceInput()
        super.onWindowHidden()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        speechRecognizer?.destroy()
        clipboardManager.removePrimaryClipChangedListener(clipChangedListener)
        if (::mlKitProofreader.isInitialized) mlKitProofreader.close()
        if (::openRouterProofreader.isInitialized) openRouterProofreader.close()
        store.clear()
        super.onDestroy()
    }

    private companion object {
        const val AUTO_PROOFREAD_DEBOUNCE_MS = 2000L

        /** How much text before the cursor the emoji suggester sees. */
        const val EMOJI_SUGGESTION_CHARS = 200
    }
}

private fun Bundle.recognitionResults(): List<String> =
    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
