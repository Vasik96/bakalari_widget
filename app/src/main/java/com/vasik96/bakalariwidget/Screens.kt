package com.vasik96.bakalariwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vasik96.bakalariwidget.data.buildSnapshot
import com.vasik96.bakalariwidget.widget.MarksWidgetReceiver
import com.vasik96.bakalariwidget.widget.TimetableWidgetReceiver
import java.text.DateFormat
import java.util.Date

@Composable
fun LoginScreen(state: UiState, onLogin: (String, String, String) -> Unit) {
    var school by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Bakaláři", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            if (state.needsLogin) "Platnost přihlášení vypršela, přihlas se znovu."
            else "Rozvrh a známky přímo na ploše.",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            school, { school = it }, Modifier.fillMaxWidth(),
            label = { Text("Adresa školy") }, placeholder = { Text("skola.bakalari.cz") },
            singleLine = true, shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            user, { user = it }, Modifier.fillMaxWidth(),
            label = { Text("Uživatelské jméno") }, singleLine = true, shape = MaterialTheme.shapes.large,
        )
        OutlinedTextField(
            pass, { pass = it }, Modifier.fillMaxWidth(),
            label = { Text("Heslo") }, singleLine = true, shape = MaterialTheme.shapes.large,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { onLogin(school, user, pass) },
            enabled = !state.loading && school.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (state.loading) CircularProgressIndicator(Modifier.size(28.dp)) else Text("Přihlásit se")
        }
    }
}

@Composable
fun DashboardScreen(state: UiState, onSync: () -> Unit, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ahoj 👋", style = MaterialTheme.typography.displaySmall)

        Card(
            Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val s = state.data?.let { buildSnapshot(it) }
                val l = s?.current ?: s?.next
                Text(
                    when {
                        s?.current != null -> "Právě teď"
                        l != null -> "Další hodina"
                        else -> "Dnes máš hotovo"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                if (l != null) {
                    Text(l.subjectFull.ifBlank { l.subject }, style = MaterialTheme.typography.headlineMedium)
                    Text("${l.start}–${l.end}  ·  ${l.room}  ·  ${l.teacher}", style = MaterialTheme.typography.bodyLarge)
                }
                state.data?.let {
                    Text(
                        "Aktualizováno ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.updatedAt))}" +
                            if (it.newMarks > 0) "  ·  ${it.newMarks} nových známek" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = onSync, enabled = !state.loading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { if (state.loading) CircularProgressIndicator(Modifier.size(28.dp)) else Text("Synchronizovat") }

        FilledTonalButton(
            onClick = { pin(ctx, TimetableWidgetReceiver::class.java) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Přidat widget Rozvrh") }

        FilledTonalButton(
            onClick = { pin(ctx, MarksWidgetReceiver::class.java) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Přidat widget Známky") }

        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Odhlásit")
        }
    }
}

private fun pin(ctx: Context, cls: Class<*>) {
    val m = AppWidgetManager.getInstance(ctx)
    if (m.isRequestPinAppWidgetSupported) m.requestPinAppWidget(ComponentName(ctx, cls), null, null)
}
