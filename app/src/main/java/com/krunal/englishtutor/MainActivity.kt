package com.krunal.englishtutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

// ============================================================
// STEP 1: Apna free Gemini API key yahan paste karo.
// Key lene ke liye: https://aistudio.google.com/app/apikey
// (Google account se login karo, "Create API key" par click karo, copy karo)
// ============================================================
const val GEMINI_API_KEY = "AQ.Ab8RN6KrdqE86tEwWR1Chuv_YGAj18M0fRMmdRwwr8Ctov7CHQ"

data class ChatMessage(val text: String, val isUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatScreen()
                }
            }
        }
    }
}

@Composable
fun ChatScreen() {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        messages.add(
            ChatMessage(
                "Hi! Type any sentence in English. I will correct the grammar and give you the Hindi translation.\n\n(Namaste! Koi bhi English sentence type karo, main grammar sahi karke Hindi translation bhi doonga.)",
                isUser = false
            )
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("English Tutor") })

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            items(messages) { msg ->
                MessageBubble(msg)
            }
            if (loading) {
                item {
                    Row(modifier = Modifier.padding(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking...")
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type your sentence...") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val userText = input.trim()
                    if (userText.isNotEmpty() && !loading) {
                        messages.add(ChatMessage(userText, isUser = true))
                        input = ""
                        loading = true
                        scope.launch {
                            val reply = callGemini(userText)
                            messages.add(ChatMessage(reply, isUser = false))
                            loading = false
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    }
                }
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(4.dp)
        ) {
            Text(
                text = msg.text,
                modifier = Modifier.padding(10.dp),
                fontWeight = if (msg.isUser) FontWeight.Normal else FontWeight.Normal
            )
        }
    }
}

// Calls Google Gemini free-tier API to correct grammar + give Hindi translation.
suspend fun callGemini(userSentence: String): String = withContext(Dispatchers.IO) {
    if (GEMINI_API_KEY == "PASTE_YOUR_API_KEY_HERE" || GEMINI_API_KEY.isBlank()) {
        return@withContext "⚠️ API key set nahi hai. MainActivity.kt mein GEMINI_API_KEY variable mein apni key paste karo."
    }

    val prompt = """
        You are an English tutor for a Hindi-speaking learner.
        The learner wrote: "$userSentence"

        Reply in EXACTLY this format, nothing else:
        Corrected: <grammatically correct English version of their sentence>
        Hindi: <Hindi translation of the corrected sentence>
        Tip: <one short, simple tip about the mistake, in Hinglish, only if there was a mistake — otherwise write "Achha likha!">
    """.trimIndent()

    val json = JSONObject().apply {
        put("contents", org.json.JSONArray().put(
            JSONObject().put("parts", org.json.JSONArray().put(
                JSONObject().put("text", prompt)
            ))
        ))
    }

    val client = OkHttpClient()
    val body = json.toString().toRequestBody("application/json".toMediaType())
    val request = Request.Builder()
        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$GEMINI_API_KEY")
        .post(body)
        .build()

    try {
        client.newCall(request).execute().use { response ->
            val respBody = response.body?.string() ?: return@use "Error: empty response"
            if (!response.isSuccessful) {
                return@use "Error ${response.code}: $respBody"
            }
            val obj = JSONObject(respBody)
            val text = obj.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            text.trim()
        }
    } catch (e: IOException) {
        "Network error: ${e.message}. Check internet connection."
    } catch (e: Exception) {
        "Error parsing response: ${e.message}"
    }
}
