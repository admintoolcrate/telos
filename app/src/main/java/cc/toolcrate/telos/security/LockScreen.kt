package cc.toolcrate.telos.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import cc.toolcrate.telos.ui.theme.LocalPaper
import kotlinx.coroutines.launch

/** Shown instead of the app while locked. Nothing behind it is drawn. */
@Composable
fun LockScreen(lock: AppLock, activity: FragmentActivity) {
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val device = lock.deviceCanAuthenticate()
    LaunchedEffect(Unit) { if (device && !lock.hasPin()) lock.promptDevice(activity) {} }

    fun submit() = scope.launch {
        message = when (val r = lock.checkPin(pin)) {
            PinResult.Ok -> null
            PinResult.Wrong -> "That's not it. Try again."
            is PinResult.Wait -> "Too many tries. Wait ${(r.millis + 999) / 1000} seconds."
        }
        pin = ""
    }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Telos", style = MaterialTheme.typography.displaySmall, color = paper.ink, modifier = Modifier.semantics { heading() })
        Text("Locked", style = MaterialTheme.typography.bodyMedium, color = paper.muted, modifier = Modifier.padding(bottom = 24.dp))
        if (lock.hasPin()) {
            OutlinedTextField(
                pin, { if (it.length <= 12 && it.all(Char::isDigit)) pin = it },
                label = { Text("PIN") }, singleLine = true, modifier = Modifier.widthIn(max = 240.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Button(onClick = { submit() }, enabled = pin.length >= 4, modifier = Modifier.padding(top = 12.dp)) { Text("Unlock") }
        }
        if (device) TextButton(onClick = { lock.promptDevice(activity) {} }) { Text("Use fingerprint, face or screen lock") }
        message?.let { Text(it, color = paper.muted, modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}
