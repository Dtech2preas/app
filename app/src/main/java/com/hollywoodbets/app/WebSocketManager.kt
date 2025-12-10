package com.hollywoodbets.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class WebSocketManager(private val listener: MessageListener) {

    interface MessageListener {
        fun onMessageReceived(text: String)
        fun onConnected()
        fun onDisconnected()
    }

    companion object {
        private const val TAG = "WebSocketManager"
        private const val WS_URL = "wss://log-in.co.za/ws"
        private const val RECONNECT_DELAY_MS = 5000L
        private const val HEARTBEAT_INTERVAL_MS = 30000L
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Disable timeout for long-lived connection
        .build()

    private var webSocket: WebSocket? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isConnected = false
    private var shouldReconnect = true

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (isConnected && webSocket != null) {
                Log.d(TAG, "Sending Heartbeat PING")
                webSocket?.send("PING")
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private val reconnectRunnable = Runnable {
        connect()
    }

    fun connect() {
        if (isConnected || webSocket != null) return

        Log.d(TAG, "Connecting to $WS_URL")
        val request = Request.Builder().url(WS_URL).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected!")
                isConnected = true

                // Handshake
                webSocket.send("READY")

                // Start Heartbeat
                handler.post(heartbeatRunnable)

                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Message received: $text")
                listener.onMessageReceived(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: $code / $reason")
                webSocket.close(1000, null)
                isConnected = false
                this@WebSocketManager.webSocket = null
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Error: " + t.message)
                isConnected = false
                this@WebSocketManager.webSocket = null
                listener.onDisconnected()
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        Log.d(TAG, "Scheduling reconnect in ${RECONNECT_DELAY_MS}ms")
        handler.removeCallbacks(reconnectRunnable) // Avoid duplicates
        handler.removeCallbacks(heartbeatRunnable) // Stop heartbeat
        handler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS)
    }

    fun send(text: String) {
        if (isConnected && webSocket != null) {
            webSocket?.send(text)
        } else {
            Log.e(TAG, "Cannot send message, not connected")
        }
    }

    fun stop() {
        shouldReconnect = false
        handler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "App closing")
        webSocket = null
    }
}
