package com.example.betterswipekeyboard

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.betterswipekeyboard.proofread.OpenRouterProofreader
import com.example.betterswipekeyboard.swipe.parseCustomWords
import com.example.betterswipekeyboard.ui.theme.BetterSwipeKeyboardTheme
import kotlinx.coroutines.delay

private val KeySavedGreen = Color(0xFF30D158)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BetterSwipeKeyboardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SetupScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SetupScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyStore = remember { ApiKeyStore(context) }
    val wordStore = remember { CustomWordStore(context) }
    var testText by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var savedKey by remember { mutableStateOf(keyStore.apiKey) }
    var inputVisible by remember { mutableStateOf(savedKey == null) }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // Runtime permissions can only be requested from an Activity — the
    // keyboard's permission panel routes the user here to grant it.
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> micGranted = granted }
    // The box IS the source of truth: pre-filled with the stored words, one
    // per line; saving replaces the whole set.
    var customWordsInput by remember {
        mutableStateOf(wordStore.load().joinToString("\n"))
    }
    var savedWordCount by remember { mutableStateOf<Int?>(null) }

    // Auto-scroll the focused text field above the soft keyboard: imePadding
    // only shrinks the viewport, and Compose's built-in focus relocation
    // fires before the IME inset lands, so without this the field stays
    // hidden behind the keyboard (verified on emulator: the ime() inset
    // arrives, the layout just never scrolls).
    val scrollState = rememberScrollState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val requesters = remember { Array(3) { BringIntoViewRequester() } }
    var focusedField by remember { mutableIntStateOf(-1) }
    LaunchedEffect(imeBottom, focusedField) {
        if (imeBottom > 0 && focusedField >= 0) {
            // The IME slide-in animates the inset over a few hundred ms, and
            // relocating against a mid-animation viewport is a no-op. Every
            // animation frame relaunches this effect, so the delay only
            // elapses once the inset has settled at its final value.
            delay(300)
            requesters[focusedField].bringIntoView()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Keep content above the soft keyboard: with edge-to-edge the
            // window is not resized for the IME, so pad for it here. Order
            // matters — modifiers after verticalScroll become part of the
            // scrollable CONTENT (the inset would just extend the scroll
            // range); before it, the padding shrinks the viewport, which is
            // what lets bringIntoView lift the focused field clear.
            .imePadding()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(
                if (isSystemInDarkTheme()) R.drawable.ic_logo_dark
                else R.drawable.ic_logo_light,
            ),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            contentScale = ContentScale.Fit,
        )
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.enable_keyboard))
        }
        Button(
            onClick = {
                context.getSystemService(InputMethodManager::class.java)
                    ?.showInputMethodPicker()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.pick_keyboard))
        }

        Text(
            text = stringResource(R.string.voice_input_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.voice_input_description),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(
                if (micGranted) R.string.mic_permission_granted
                else R.string.mic_permission_denied
            ),
            color = if (micGranted) KeySavedGreen else Color.Unspecified,
            style = MaterialTheme.typography.bodySmall,
        )
        if (!micGranted) {
            Button(
                onClick = {
                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                },
            ) {
                Text(stringResource(R.string.grant_mic_permission))
            }
        }

        Text(
            text = stringResource(R.string.proofreader_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(
                R.string.proofreader_description,
                OpenRouterProofreader.MODEL,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        if (inputVisible) {
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text(stringResource(R.string.api_key_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(requesters[0])
                    .onFocusChanged { focusedField = if (it.isFocused) 0 else -1 },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (inputVisible) {
                Button(
                    onClick = {
                        keyStore.save(apiKeyInput)
                        savedKey = keyStore.apiKey
                        apiKeyInput = ""
                        inputVisible = false
                    },
                    enabled = apiKeyInput.isNotBlank(),
                ) {
                    Text(stringResource(R.string.save_key))
                }
            }
            if (savedKey != null) {
                OutlinedButton(
                    onClick = {
                        keyStore.clear()
                        savedKey = null
                        inputVisible = true
                    },
                ) {
                    Text(stringResource(R.string.clear_key))
                }
                if (!inputVisible) {
                    OutlinedButton(
                        onClick = {
                            apiKeyInput = savedKey.orEmpty()
                            inputVisible = true
                        },
                    ) {
                        Text(stringResource(R.string.change_key))
                    }
                }
            }
        }
        Text(
            text = if (savedKey != null) {
                stringResource(R.string.api_key_saved)
            } else {
                stringResource(R.string.api_key_get)
            },
            color = if (savedKey != null) KeySavedGreen else Color.Unspecified,
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            text = stringResource(R.string.custom_words_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.custom_words_description),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = customWordsInput,
            onValueChange = { customWordsInput = it },
            label = { Text(stringResource(R.string.custom_words_hint)) },
            minLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(requesters[2])
                .onFocusChanged { focusedField = if (it.isFocused) 2 else -1 },
        )
        Button(
            onClick = {
                // Parse, normalize and show back what was actually understood.
                val words = parseCustomWords(customWordsInput)
                wordStore.save(words)
                customWordsInput = words.joinToString("\n")
                savedWordCount = words.size
            },
        ) {
            Text(stringResource(R.string.save_words))
        }
        savedWordCount?.let { count ->
            Text(
                text = stringResource(R.string.custom_words_saved, count),
                color = KeySavedGreen,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        OutlinedTextField(
            value = testText,
            onValueChange = { testText = it },
            label = { Text(stringResource(R.string.test_field_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(requesters[1])
                .onFocusChanged { focusedField = if (it.isFocused) 1 else -1 },
        )
    }
}
