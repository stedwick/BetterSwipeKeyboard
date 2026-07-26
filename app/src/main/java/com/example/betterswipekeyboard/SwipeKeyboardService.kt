package com.example.betterswipekeyboard

import android.inputmethodservice.InputMethodService
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
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
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
import com.example.betterswipekeyboard.proofread.MlKitProofreader
import com.example.betterswipekeyboard.proofread.OpenRouterProofreader
import com.example.betterswipekeyboard.proofread.ProofreadMode
import com.example.betterswipekeyboard.proofread.Proofreader
import com.example.betterswipekeyboard.proofread.ProofreaderBackend
import com.example.betterswipekeyboard.proofread.ProofreaderStatus
import com.example.betterswipekeyboard.proofread.SentenceExtractor
import com.example.betterswipekeyboard.proofread.selectBackend
import com.example.betterswipekeyboard.swipe.Dictionary
import com.example.betterswipekeyboard.swipe.SwipeDecoder
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
    private lateinit var decoder: SwipeDecoder
    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var mlKitProofreader: MlKitProofreader
    private lateinit var openRouterProofreader: OpenRouterProofreader

    /** The backend currently backing the sparkly button, or null when none. */
    private var activeProofreader: Proofreader? = null

    private val editor = InputConnectionEditor { currentInputConnection }
    private var autoProofreadJob: Job? = null
    private var lastCommitWasSwipe = false

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

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        viewModel = ViewModelProvider(this)[KeyboardViewModel::class.java]
        decoder = SwipeDecoder(Dictionary.load(assets.open("words_en.txt")))
        apiKeyStore = ApiKeyStore(this)
        mlKitProofreader = MlKitProofreader(this)
        openRouterProofreader = OpenRouterProofreader(apiKeyStore)
        lifecycleScope.launch { refreshProofreader() }
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
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return ComposeView(this).apply {
            setContent {
                val state by viewModel.state.collectAsState()
                KeyboardScreen(
                    state = state,
                    decoder = decoder,
                    onAction = ::onKeyboardAction,
                    onSettingsClick = ::openMainApp,
                    onPermissionHelpClick = ::openMainApp,
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
        // Keep the status bar fresh: the model may have finished downloading
        // or the API key may have changed since the last check.
        lifecycleScope.launch { refreshProofreader() }
    }

    /**
     * The default implementation hides the soft keyboard when a hardware
     * keyboard is attached (e.g. on emulators). We always want to show.
     */
    override fun onEvaluateInputViewShown(): Boolean = true

    private fun onKeyboardAction(action: KeyboardAction) {
        // Voice start/stop is decided here (permission + availability checks
        // are Android concerns); the ViewModel just records the outcome.
        if (action is KeyboardAction.ToggleVoice) {
            onToggleVoice()
            return
        }
        when (val effect = viewModel.onAction(action)) {
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
                scheduleAutoProofread()
            }
            is KeyboardEffect.CommitWord -> {
                // commitWord inserts the leading space for tap → swipe itself.
                editor.commitWord(effect.word)
                lastCommitWasSwipe = true
                scheduleAutoProofread()
            }
            KeyboardEffect.DeleteBackward -> {
                editor.backspace()
                scheduleAutoProofread()
            }
            KeyboardEffect.PerformEnter -> {
                editor.enter(currentInputEditorInfo)
                scheduleAutoProofread()
            }
            null -> Unit
        }
    }

    /**
     * Auto-proofreading debounce: every text change restarts a 1-second
     * timer; the proofread fires only after a full second of typing
     * inactivity (so at most once per second, never mid-thought). Dictated
     * text schedules with [ProofreadMode.VOICE] so the proofreader targets
     * speech-recognition errors; the debounce is shared, so typing right
     * after dictating reschedules (last writer wins).
     */
    private fun scheduleAutoProofread(mode: ProofreadMode = ProofreadMode.TYPED) {
        if (!viewModel.state.value.proofreadAuto) return
        autoProofreadJob?.cancel()
        autoProofreadJob = lifecycleScope.launch {
            delay(AUTO_PROOFREAD_DEBOUNCE_MS)
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
                val sentence = SentenceExtractor.currentSentence(before)
                if (sentence.isEmpty()) return@launch
                val corrected = proofreader.proofread(sentence.trim(), mode)
                // The user may have kept typing while the request was in
                // flight; never clobber newer text.
                val latest = SentenceExtractor.currentSentence(
                    editor.textBeforeCursor().orEmpty(),
                )
                if (latest == sentence &&
                    corrected.isNotBlank() &&
                    corrected != sentence.trim()
                ) {
                    // Preserve the fragment's surrounding whitespace.
                    editor.replaceBeforeCursor(
                        sentence.length,
                        sentence.replace(sentence.trim(), corrected),
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
     * the sentence is voice-proofread after the usual 1s debounce.
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
        if (::mlKitProofreader.isInitialized) mlKitProofreader.close()
        if (::openRouterProofreader.isInitialized) openRouterProofreader.close()
        store.clear()
        super.onDestroy()
    }

    private companion object {
        const val AUTO_PROOFREAD_DEBOUNCE_MS = 1000L
    }
}

private fun Bundle.recognitionResults(): List<String> =
    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
