package com.example.betterswipekeyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
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
import com.example.betterswipekeyboard.proofread.Proofreader
import com.example.betterswipekeyboard.proofread.SentenceExtractor
import com.example.betterswipekeyboard.swipe.Dictionary
import com.example.betterswipekeyboard.swipe.SwipeDecoder
import com.example.betterswipekeyboard.ui.keyboard.KeyboardScreen
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
    private lateinit var proofreader: Proofreader
    private val editor = InputConnectionEditor { currentInputConnection }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        viewModel = ViewModelProvider(this)[KeyboardViewModel::class.java]
        decoder = SwipeDecoder(Dictionary.load(assets.open("words_en.txt")))
        proofreader = MlKitProofreader(this)
        lifecycleScope.launch {
            viewModel.setProofreaderStatus(proofreader.status())
        }
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
                )
            }
        }
    }

    /**
     * The default implementation hides the soft keyboard when a hardware
     * keyboard is attached (e.g. on emulators). We always want to show.
     */
    override fun onEvaluateInputViewShown(): Boolean = true

    private fun onKeyboardAction(action: KeyboardAction) {
        when (val effect = viewModel.onAction(action)) {
            is KeyboardEffect.CommitText -> editor.commitText(effect.text)
            KeyboardEffect.DeleteBackward -> editor.backspace()
            KeyboardEffect.PerformEnter -> editor.enter(currentInputEditorInfo)
            KeyboardEffect.Proofread -> runProofread()
            null -> Unit
        }
    }

    /**
     * Fixes the current sentence with the on-device AI proofreader. The text
     * never leaves the device. Failures are logged and otherwise ignored —
     * the keyboard must never depend on the AI being present.
     */
    private fun runProofread() {
        if (viewModel.state.value.proofreadInFlight) return
        viewModel.setProofreadInFlight(true)
        lifecycleScope.launch {
            try {
                val before = editor.textBeforeCursor().orEmpty()
                val sentence = SentenceExtractor.currentSentence(before)
                if (sentence.isEmpty()) return@launch
                val corrected = proofreader.proofread(sentence.trim())
                if (corrected.isNotBlank() && corrected != sentence.trim()) {
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

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (::proofreader.isInitialized) proofreader.close()
        store.clear()
        super.onDestroy()
    }
}
