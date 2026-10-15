package com.silence.blocker.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silence.blocker.ui.theme.ScreenTitle
import com.silence.blocker.ui.theme.SilenceCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silence.blocker.SilenceApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun RulesScreen() {
    val app = LocalContext.current.applicationContext as SilenceApp
    val scope = rememberCoroutineScope()
    val allow by app.userRulesStore.allowlist.collectAsStateWithLifecycle(emptySet())
    val block by app.userRulesStore.blocklist.collectAsStateWithLifecycle(emptySet())
    var input by remember { mutableStateOf("") }

    suspend fun refreshEngine() {
        app.filterEngine.applyUserRules(
            allow = app.settingsStore.allowlist.first(),
            block = app.settingsStore.blocklist.first(),
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Custom rules")
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("domain.com") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val d = input.trim()
                    if (d.isNotEmpty()) {
                        scope.launch {
                            app.userRulesStore.block(d)
                            input = ""
                            refreshEngine()
                        }
                    }
                },
            ) { Text("Block") }
            OutlinedButton(
                onClick = {
                    val d = input.trim()
                    if (d.isNotEmpty()) {
                        scope.launch {
                            app.userRulesStore.allow(d)
                            input = ""
                            refreshEngine()
                        }
                    }
                },
            ) { Text("Allow") }
        }

        Text("Allowlist", fontWeight = FontWeight.SemiBold)
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(allow.toList().sorted()) { domain ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(domain)
                    TextButton(onClick = {
                        scope.launch {
                            app.userRulesStore.removeAllow(domain)
                            refreshEngine()
                        }
                    }) { Text("Remove") }
                }
            }
        }

        Text("Blocklist", fontWeight = FontWeight.SemiBold)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(block.toList().sorted()) { domain ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(domain)
                    TextButton(onClick = {
                        scope.launch {
                            app.userRulesStore.removeBlock(domain)
                            refreshEngine()
                        }
                    }) { Text("Remove") }
                }
            }
        }
    }
}
