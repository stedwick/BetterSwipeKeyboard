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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.betterswipekeyboard.ui.theme.BetterSwipeKeyboardTheme

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
    var testText by remember { mutableStateOf("") }

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
        OutlinedTextField(
            value = testText,
            onValueChange = { testText = it },
            label = { Text(stringResource(R.string.test_field_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
