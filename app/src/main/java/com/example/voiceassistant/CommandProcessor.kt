package com.example.voiceassistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar

/**
 * Processes voice commands and triggers the right action.
 * Mirrors the original desktop assistant's command set, adapted
 * for what is actually possible on Android (no shutdown/kill-process/
 * brightness-via-wmi/keyboard-simulation — those don't apply on mobile).
 */
class CommandProcessor(
    private val context: Context,
    private val speak: (String) -> Unit
) {
    private val client = OkHttpClient()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val jokes = listOf(
        "Why do programmers prefer dark mode? Because light attracts bugs.",
        "I told my computer I needed a break, and it said no problem, I'll go to sleep.",
        "Why did the developer go broke? Because he used up all his cache.",
        "There are only 10 types of people: those who understand binary and those who don't."
    )

    fun process(rawCommand: String) {
        val command = rawCommand.lowercase().trim()
        if (command.isEmpty()) return

        when {
            containsAny(command, "hello", "hi", "hey", "good morning", "good evening") ->
                speak(listOf(
                    "Hello! How can I assist you today?",
                    "Hi there! What can I do for you?",
                    "Hey! Ready to help."
                ).random())

            containsAny(command, "how are you") ->
                speak("I'm doing great, thank you! How can I help you today?")

            containsAny(command, "your name") ->
                speak("My name is Assistant, your personal voice assistant.")

            containsAny(command, "what can you do") ->
                speak("I can tell the time, search the web, open apps, tell jokes, check weather, calculate, and more.")

            containsAny(command, "thank you", "thanks") ->
                speak("You're welcome!")

            containsAny(command, "time") -> tellTime()
            containsAny(command, "date") -> tellDate()
            containsAny(command, "day") -> tellDay()

            containsAny(command, "wikipedia", "wiki") -> searchWikipedia(stripKeyword(command, "wikipedia", "wiki"))
            containsAny(command, "search", "google") -> webSearch(stripKeyword(command, "search", "google"))

            containsAny(command, "open") -> openApp(stripKeyword(command, "open"))

            containsAny(command, "volume up", "increase volume", "louder") -> adjustVolume(true)
            containsAny(command, "volume down", "decrease volume", "quieter") -> adjustVolume(false)
            containsAny(command, "mute") -> setMute(true)
            containsAny(command, "unmute") -> setMute(false)

            containsAny(command, "flashlight", "torch") -> speak("Flashlight control needs camera permission wiring in MainActivity — hook it up to CameraManager there.")

            containsAny(command, "joke") -> speak(jokes.random())

            containsAny(command, "weather") -> getWeather(stripKeyword(command, "weather", "in"))

            containsAny(command, "calculate", "math") -> calculate(stripKeyword(command, "calculate", "math"))

            containsAny(command, "battery") -> checkBattery()

            containsAny(command, "call") -> makeCall(stripKeyword(command, "call"))

            containsAny(command, "settings") -> openSettings()

            containsAny(command, "youtube") -> webSearch("", directUrl = "https://www.youtube.com")
            containsAny(command, "gmail", "email") -> webSearch("", directUrl = "https://mail.google.com")

            containsAny(command, "help", "commands") -> speak("You can say: time, date, search, wikipedia, open an app, weather, calculate, joke, volume up or down, battery, or call someone.")

            containsAny(command, "exit", "quit", "goodbye", "bye") -> speak("Goodbye! Have a great day!")

            else -> {
                speak("I didn't understand that. Let me search it for you.")
                webSearch(command)
            }
        }
    }

    private fun containsAny(text: String, vararg keys: String) = keys.any { text.contains(it) }

    private fun stripKeyword(command: String, vararg keywords: String): String {
        var result = command
        keywords.forEach { result = result.replace(it, "") }
        return result.trim()
    }

    private fun tellTime() {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR)
        val minute = cal.get(Calendar.MINUTE)
        val amPm = if (cal.get(Calendar.AM_PM) == 0) "AM" else "PM"
        speak("The current time is ${if (hour == 0) 12 else hour}:${"%02d".format(minute)} $amPm")
    }

    private fun tellDate() {
        val cal = Calendar.getInstance()
        val months = listOf("January","February","March","April","May","June","July","August","September","October","November","December")
        speak("Today is ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.DAY_OF_MONTH)}, ${cal.get(Calendar.YEAR)}")
    }

    private fun tellDay() {
        val cal = Calendar.getInstance()
        val days = listOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
        speak("Today is ${days[cal.get(Calendar.DAY_OF_WEEK) - 1]}")
    }

    private fun searchWikipedia(query: String) {
        if (query.isEmpty()) { speak("What would you like to search on Wikipedia?"); return }
        speak("Searching Wikipedia for $query")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://en.wikipedia.org/api/rest_v1/page/summary/${Uri.encode(query)}"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        val extract = json.optString("extract", "No summary found.")
                        withMain { speak(extract) }
                    } else {
                        withMain { speak("Couldn't find anything about $query on Wikipedia.") }
                    }
                }
            } catch (e: Exception) {
                withMain { speak("Error reaching Wikipedia: ${e.message}") }
            }
        }
    }

    private fun webSearch(query: String, directUrl: String? = null) {
        val url = directUrl ?: run {
            if (query.isEmpty()) { speak("What would you like to search for?"); return }
            "https://www.google.com/search?q=${Uri.encode(query)}"
        }
        if (directUrl == null) speak("Searching for $query")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            speak("I couldn't open a browser to search that.")
        }
    }

    private val appPackageMap = mapOf(
        "chrome" to "com.android.chrome",
        "youtube" to "com.google.android.youtube",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "camera" to "com.android.camera",
        "calculator" to "com.google.android.calculator",
        "settings" to "com.android.settings",
        "whatsapp" to "com.whatsapp",
        "spotify" to "com.spotify.music",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "play store" to "com.android.vending"
    )

    private fun openApp(appName: String) {
        if (appName.isEmpty()) { speak("Which app would you like to open?"); return }
        val pkg = appPackageMap.entries.firstOrNull { appName.contains(it.key) }?.value
        if (pkg != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                speak("Opening $appName")
            } else {
                speak("$appName doesn't seem to be installed.")
            }
        } else {
            speak("I don't recognize $appName as a known app. Searching for it instead.")
            webSearch(appName)
        }
    }

    private fun adjustVolume(up: Boolean) {
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        speak(if (up) "Volume increased" else "Volume decreased")
    }

    private fun setMute(mute: Boolean) {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
            0
        )
        speak(if (mute) "Volume muted" else "Volume unmuted")
    }

    private fun getWeather(city: String) {
        if (city.isEmpty()) { speak("Which city would you like the weather for?"); return }
        speak("Checking weather for $city")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(city)}&count=1"
                val geoRequest = Request.Builder().url(geoUrl).build()
                client.newCall(geoRequest).execute().use { geoResponse ->
                    val geoBody = geoResponse.body?.string() ?: return@use
                    val geoJson = JSONObject(geoBody)
                    val results = geoJson.optJSONArray("results")
                    if (results == null || results.length() == 0) {
                        withMain { speak("Could not find weather data for $city") }
                        return@use
                    }
                    val first = results.getJSONObject(0)
                    val lat = first.getDouble("latitude")
                    val lon = first.getDouble("longitude")
                    val name = first.getString("name")

                    val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
                    val weatherRequest = Request.Builder().url(weatherUrl).build()
                    client.newCall(weatherRequest).execute().use { weatherResponse ->
                        val weatherBody = weatherResponse.body?.string() ?: return@use
                        val weatherJson = JSONObject(weatherBody).getJSONObject("current_weather")
                        val temp = weatherJson.getDouble("temperature")
                        val wind = weatherJson.getDouble("windspeed")
                        withMain { speak("Weather in $name: temperature $temp degrees Celsius, wind speed $wind kilometers per hour.") }
                    }
                }
            } catch (e: Exception) {
                withMain { speak("Sorry, I couldn't get the weather. ${e.message}") }
            }
        }
    }

    private fun calculate(expressionRaw: String) {
        if (expressionRaw.isEmpty()) { speak("What would you like me to calculate?"); return }
        var expr = expressionRaw
            .replace("plus", "+")
            .replace("minus", "-")
            .replace("times", "*")
            .replace("multiplied by", "*")
            .replace("divided by", "/")
            .replace("over", "/")

        val cleaned = expr.filter { it.isDigit() || it in "+-*/.() " }
        try {
            val result = evalExpression(cleaned)
            speak("The result is $result")
        } catch (e: Exception) {
            speak("I couldn't calculate that. Please try a simpler expression.")
        }
    }

    // Minimal safe arithmetic evaluator (+,-,*,/, parentheses) — no eval() on Android.
    private fun evalExpression(expr: String): Double {
        val tokens = tokenize(expr)
        val (value, _) = parseExpr(tokens, 0)
        return value
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var num = StringBuilder()
        for (c in expr) {
            when {
                c.isDigit() || c == '.' -> num.append(c)
                c in "+-*/()" -> {
                    if (num.isNotEmpty()) { tokens.add(num.toString()); num = StringBuilder() }
                    tokens.add(c.toString())
                }
                c == ' ' -> if (num.isNotEmpty()) { tokens.add(num.toString()); num = StringBuilder() }
            }
        }
        if (num.isNotEmpty()) tokens.add(num.toString())
        return tokens
    }

    private fun parseExpr(tokens: List<String>, startIndex: Int): Pair<Double, Int> {
        var (value, index) = parseTerm(tokens, startIndex)
        while (index < tokens.size && (tokens[index] == "+" || tokens[index] == "-")) {
            val op = tokens[index]
            val (rhs, nextIndex) = parseTerm(tokens, index + 1)
            value = if (op == "+") value + rhs else value - rhs
            index = nextIndex
        }
        return Pair(value, index)
    }

    private fun parseTerm(tokens: List<String>, startIndex: Int): Pair<Double, Int> {
        var (value, index) = parseFactor(tokens, startIndex)
        while (index < tokens.size && (tokens[index] == "*" || tokens[index] == "/")) {
            val op = tokens[index]
            val (rhs, nextIndex) = parseFactor(tokens, index + 1)
            value = if (op == "*") value * rhs else value / rhs
            index = nextIndex
        }
        return Pair(value, index)
    }

    private fun parseFactor(tokens: List<String>, startIndex: Int): Pair<Double, Int> {
        val token = tokens[startIndex]
        return when {
            token == "-" -> {
                val (value, nextIndex) = parseFactor(tokens, startIndex + 1)
                Pair(-value, nextIndex)
            }
            token == "(" -> {
                val (value, nextIndex) = parseExpr(tokens, startIndex + 1)
                Pair(value, nextIndex + 1) // skip ')'
            }
            else -> Pair(token.toDouble(), startIndex + 1)
        }
    }

    private fun checkBattery() {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        speak("Battery is at $level percent")
    }

    private fun makeCall(name: String) {
        if (name.isEmpty()) { speak("Who would you like to call?"); return }
        val intent = Intent(Intent.ACTION_DIAL)
        try {
            context.startActivity(intent)
            speak("Opening dialer for $name")
        } catch (e: Exception) {
            speak("I couldn't open the dialer.")
        }
    }

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_SETTINGS)
        try {
            context.startActivity(intent)
            speak("Opening settings")
        } catch (e: Exception) {
            speak("I couldn't open settings.")
        }
    }

    private suspend fun withMain(action: () -> Unit) {
        kotlinx.coroutines.withContext(Dispatchers.Main) { action() }
    }
}
