package com.antigravity.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private RecyclerView rvMessages;
    private EditText etPrompt;
    private MaterialButton btnSend;
    private TextView tvStatus;
    private LinearLayout thinkingContainer;
    private TextView tvThinkingText;

    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messageList = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rvMessages = findViewById(R.id.rvMessages);
        etPrompt = findViewById(R.id.etPrompt);
        btnSend = findViewById(R.id.btnSend);
        tvStatus = findViewById(R.id.tvStatus);
        thinkingContainer = findViewById(R.id.thinkingContainer);
        tvThinkingText = findViewById(R.id.tvThinkingText);

        chatAdapter = new ChatAdapter(messageList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(chatAdapter);

        // Add initial greeting
        messageList.add(new ChatMessage("Halo! Selamat datang di Antigravity AI.\nSilakan masukkan prompt atau instruksi coding untuk memulai.", ChatMessage.TYPE_AGENT));
        chatAdapter.notifyItemInserted(0);

        btnSend.setOnClickListener(v -> handleSendPrompt());
    }

    private void handleSendPrompt() {
        String prompt = etPrompt.getText().toString().trim();
        if (prompt.isEmpty()) return;

        // 1. Add User Message
        messageList.add(new ChatMessage(prompt, ChatMessage.TYPE_USER));
        int userPos = messageList.size() - 1;
        chatAdapter.notifyItemInserted(userPos);
        rvMessages.smoothScrollToPosition(userPos);

        etPrompt.setText("");
        etPrompt.setEnabled(false);
        btnSend.setEnabled(false);

        // 2. Set State to Thinking
        tvStatus.setText("THINKING");
        tvThinkingText.setText("Memproses prompt & merencanakan respon...");
        thinkingContainer.setVisibility(View.VISIBLE);

        // 3. Simulate processing and stream tokens
        handler.postDelayed(() -> {
            tvStatus.setText("RUNNING TOOL");
            tvThinkingText.setText("⚙ Menjalankan analisis context...");

            handler.postDelayed(() -> {
                thinkingContainer.setVisibility(View.GONE);
                tvStatus.setText("GENERATING");

                // Start streaming response
                String fullResponse = "Menerima instruksi: \"" + prompt + "\".\n\nRespon berhasil diproses secara mulus pada Android AI Interface.\nStatus: OK";
                String[] words = fullResponse.split(" ");
                StringBuilder streamedText = new StringBuilder();

                messageList.add(new ChatMessage("", ChatMessage.TYPE_AGENT));
                int agentPos = messageList.size() - 1;
                chatAdapter.notifyItemInserted(agentPos);

                streamWords(words, 0, streamedText, agentPos);
            }, 700);
        }, 600);
    }

    private void streamWords(String[] words, int index, StringBuilder builder, int position) {
        if (index >= words.length) {
            tvStatus.setText("READY");
            etPrompt.setEnabled(true);
            btnSend.setEnabled(true);
            return;
        }

        builder.append(words[index]).append(" ");
        messageList.set(position, new ChatMessage(builder.toString(), ChatMessage.TYPE_AGENT));
        chatAdapter.notifyItemChanged(position);
        rvMessages.smoothScrollToPosition(position);

        handler.postDelayed(() -> streamWords(words, index + 1, builder, position), 35);
    }
}
