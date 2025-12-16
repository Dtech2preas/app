package com.dtech.scanner;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private EditText etHost;
    private Button btnScan;
    private TextView tvConsole;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private HostScanner hostScanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etHost = findViewById(R.id.etHost);
        btnScan = findViewById(R.id.btnScan);
        tvConsole = findViewById(R.id.tvConsole);
        hostScanner = new HostScanner();

        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String host = etHost.getText().toString().trim();
                if (host.isEmpty()) {
                    appendLog("Error: Please enter a host.");
                    return;
                }

                // Clear console or just append separator
                tvConsole.setText("> Ready...\n");

                btnScan.setEnabled(false);

                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        hostScanner.scan(host, new HostScanner.ScanCallback() {
                            @Override
                            public void onLog(final String message) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        appendLog(message);
                                    }
                                });
                            }

                            @Override
                            public void onResult(final String message) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        appendLog(message);
                                        btnScan.setEnabled(true);
                                    }
                                });
                            }

                            @Override
                            public void onError(final String error) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        appendLog(error);
                                        btnScan.setEnabled(true);
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    private void appendLog(String message) {
        tvConsole.append("\n" + message);
        // Auto-scroll could be added here if needed, but simple append works for now
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
