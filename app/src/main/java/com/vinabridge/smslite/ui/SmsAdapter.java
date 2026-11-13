package com.vinabridge.smslite.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.vinabridge.smslite.R;
import com.vinabridge.smslite.model.SmsPayload;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmsAdapter extends RecyclerView.Adapter<SmsAdapter.SmsViewHolder> {
    private final List<SmsPayload> messages = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());

    public void setMessages(List<SmsPayload> list) {
        messages.clear();
        if (list != null) {
            messages.addAll(list);
        }
        notifyDataSetChanged();
    }

    public void addMessageToTop(SmsPayload payload) {
        if (payload == null) return;
        // Avoid duplicate ID
        for (SmsPayload item : messages) {
            if (item.getId().equals(payload.getId())) return;
        }
        messages.add(0, payload);
        notifyItemInserted(0);
    }

    @NonNull
    @Override
    public SmsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sms_message, parent, false);
        return new SmsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SmsViewHolder holder, int position) {
        SmsPayload item = messages.get(position);
        holder.tvSender.setText(item.getSender().isEmpty() ? "Không rõ" : item.getSender());
        holder.tvTime.setText(dateFormat.format(new Date(item.getTimestamp())));
        holder.tvBody.setText(item.getBody());

        holder.btnCopy.setOnClickListener(v -> {
            Context ctx = v.getContext();
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = ClipData.newPlainText("SMS OTP", item.getBody());
                cm.setPrimaryClip(clip);
                Toast.makeText(ctx, R.string.receiver_copy_success, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class SmsViewHolder extends RecyclerView.ViewHolder {
        TextView tvSender;
        TextView tvTime;
        TextView tvBody;
        Button btnCopy;

        public SmsViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tv_sender);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvBody = itemView.findViewById(R.id.tv_body);
            btnCopy = itemView.findViewById(R.id.btn_copy);
        }
    }
}
