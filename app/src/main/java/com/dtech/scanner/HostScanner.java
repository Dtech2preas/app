package com.dtech.scanner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class HostScanner {

    private boolean isSslRequired = false;

    // State variables for Analysis Engine
    private boolean http200 = false;
    private boolean sniSuccess = false;
    private boolean ws101 = false;
    private boolean dataTransfer = false;
    private boolean methodConnect = false;

    public interface ScanCallback {
        void onLog(String message);
        void onResult(String message);
        void onError(String error);
    }

    public void scan(String host, ScanCallback callback) {
        // Reset state for new scan
        isSslRequired = false;
        http200 = false;
        sniSuccess = false;
        ws101 = false;
        dataTransfer = false;
        methodConnect = false;

        callback.onLog(">> Starting Deep Scan for: " + host);

        // Step A: The Pulse Check (HTTP)
        performPulseCheck(host, callback);

        // Step B: Tunnel Capability Tests
        callback.onLog("\n>> Starting Tunnel Capability Tests...");

        // 1. WebSocket Probe
        performWebSocketProbe(host, callback);

        // 2. SNI/SSL Handshake & Data Pipe Test
        performSniHandshake(host, callback);

        // 3. Method Enumeration
        performMethodEnumeration(host, callback);

        // Final Step: Analysis
        analyzeCompatibility(callback);

        callback.onResult("\n>> Deep Scan Complete.");
    }

    private boolean performPulseCheck(String host, ScanCallback callback) {
        callback.onLog(">> [Step A] Pulse Check (HTTP HEAD)...");
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://" + host);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            callback.onLog("   Response Code: " + responseCode);

            if (responseCode == 200) {
                http200 = true;
                callback.onLog("   HTTP Direct Success");
                return true;
            } else if (responseCode == 301 || responseCode == 302) {
                callback.onLog("   HTTP Redirected (Potential Port 80 Block). Proceeding to SSL Check...");
                String location = connection.getHeaderField("Location");
                if (location != null) {
                    callback.onLog("   Location: " + location);
                }
                return true;
            } else {
                callback.onLog("   RESULT: Blocked/Other (" + responseCode + ")");
                return true;
            }

        } catch (IOException e) {
            callback.onLog("   HTTP Unreachable. Proceeding to SSL Check...");
            return true;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void performWebSocketProbe(String host, ScanCallback callback) {
        callback.onLog(">> [Step B.1] WebSocket Probe (Port 80)...");
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, 80), 5000);

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            String request = "GET / HTTP/1.1\r\n" +
                             "Host: " + host + "\r\n" +
                             "Connection: Upgrade\r\n" +
                             "Upgrade: websocket\r\n" +
                             "\r\n";

            out.write(request.getBytes());
            out.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String statusLine = reader.readLine();

            if (statusLine != null) {
                 callback.onLog("   Response: " + statusLine);
                 if (statusLine.contains("101")) {
                     ws101 = true;
                     callback.onLog("   RESULT: WebSocket Upgrade SUCCESS (101 Switching Protocols)");
                 } else {
                     callback.onLog("   RESULT: Failed (Not 101)");
                 }
            } else {
                callback.onLog("   RESULT: No Response");
            }

        } catch (IOException e) {
             callback.onLog("   RESULT: Connection Failed (" + e.getMessage() + ")");
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    private void performSniHandshake(String host, ScanCallback callback) {
        callback.onLog(">> [Step B.2] SNI/SSL Handshake (Port 443)...");
        SSLSocket socket = null;
        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            socket = (SSLSocket) factory.createSocket();
            socket.connect(new InetSocketAddress(host, 443), 5000);
            socket.startHandshake();

            sniSuccess = true;
            callback.onLog("   RESULT: SNI Supported (Handshake Successful)");

            // Data Pipe Test
            String request = "GET / HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes());
            out.flush();

            // Read 1 byte
            socket.setSoTimeout(5000);
            InputStream in = socket.getInputStream();
            int read = in.read();

            if (read != -1) {
                dataTransfer = true;
                callback.onLog("   >> SSL Data Pipe: ACTIVE (Bytes received)");
            } else {
                callback.onLog("   >> SSL Data Pipe: STALLED (Handshake OK, but no data)");
            }

        } catch (IOException e) {
            callback.onLog("   RESULT: Handshake Failed (" + e.getMessage() + ")");
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    private void performMethodEnumeration(String host, ScanCallback callback) {
        callback.onLog(">> [Step B.3] Method Enumeration (OPTIONS)...");
        HttpURLConnection connection = null;
        try {
            // Note: Using HTTP for method enumeration as per common injection techniques,
            // but usually this is done on the established tunnel.
            // Assuming direct HTTP request to host as per instructions.
            String protocol = isSslRequired ? "https://" : "http://";
            URL url = new URL(protocol + host);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("OPTIONS");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            // Trigger request
            connection.getResponseCode();

            String allowHeader = connection.getHeaderField("Allow");
            if (allowHeader != null && !allowHeader.isEmpty()) {
                callback.onLog("   Allow Header: " + allowHeader);
                callback.onLog("   RESULT: Methods Enumerated");

                if (allowHeader.toUpperCase().contains("CONNECT")) {
                    methodConnect = true;
                }
            } else {
                callback.onLog("   RESULT: No 'Allow' header found.");

                // Fallback: Check if we got a response at all
                Map<String, List<String>> headers = connection.getHeaderFields();
                 if (headers != null && !headers.isEmpty()) {
                     callback.onLog("   (Received other headers, server is active)");
                 }
            }

        } catch (IOException e) {
            callback.onLog("   RESULT: Request Failed (" + e.getMessage() + ")");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void analyzeCompatibility(ScanCallback callback) {
        StringBuilder report = new StringBuilder();
        report.append("\n=============================\n");
        report.append("COMPATIBILITY VERDICT:\n");

        // HA Tunnel / TLS Tunnel (SNI)
        if (dataTransfer) {
            report.append("[v] HA Tunnel / TLS Tunnel:   COMPATIBLE (SNI)\n");
        } else if (sniSuccess) {
            report.append("[!] HA Tunnel / TLS Tunnel:   PARTIAL (Handshake OK, No Data)\n");
        } else {
             report.append("[x] HA Tunnel / TLS Tunnel:   FAILED\n");
        }

        // HTTP Injector (Direct)
        if (http200) {
             report.append("[v] HTTP Injector (Direct):   COMPATIBLE\n");
        } else {
             report.append("[x] HTTP Injector (Direct):   FAILED (Redirected/Blocked)\n");
        }

        // WebSocket Mode
        if (ws101) {
             report.append("[v] WebSocket Mode:           COMPATIBLE\n");
        } else {
             report.append("[?] WebSocket Mode:           UNTESTED/FAILED\n");
        }

        // NapsternetV / HTTP Proxy (Method)
        if (methodConnect) {
            report.append("[v] NapsternetV / HTTP Proxy: COMPATIBLE (CONNECT Allowed)\n");
        } else {
            report.append("[?] NapsternetV / HTTP Proxy: UNTESTED/FAILED\n");
        }

        report.append("===========\n");

        // Recommendations
        report.append("RECOMMENDED VPN CONFIG:\n");
        boolean anyRec = false;

        if (dataTransfer) {
            report.append("- TLS Tunnel / HA Tunnel Plus (SNI Mode)\n");
            anyRec = true;
        }
        if (ws101) {
            report.append("- HTTP Injector / Custom (WebSocket/V2Ray)\n");
            anyRec = true;
        }
        if (http200) {
            report.append("- HTTP Injector (Direct Payload)\n");
            anyRec = true;
        }
        if (methodConnect) {
            report.append("- NapsternetV / HTTP Proxy\n");
            anyRec = true;
        }

        if (!anyRec) {
            report.append("- None. Host appears fully blocked or incompatible.\n");
        }

        callback.onResult(report.toString());
    }
}
