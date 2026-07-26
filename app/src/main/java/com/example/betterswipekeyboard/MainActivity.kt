package com.example.betterswipekeyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.betterswipekeyboard.proofread.OpenRouterProofreader
import com.example.betterswipekeyboard.swipe.parseCustomWords
import com.example.betterswipekeyboard.ui.theme.BetterSwipeKeyboardTheme

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
    // The box IS the source of truth: pre-filled with the stored words, one
    // per line; saving replaces the whole set.
    var customWordsInput by remember {
        mutableStateOf(wordStore.load().joinToString("\n"))
    }
    var savedWordCount by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
                modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
