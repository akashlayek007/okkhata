package com.example.simplekhata.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.simplekhata.models.LedgerTransaction;
import com.example.simplekhata.models.TransactionType;
import com.example.simplekhata.utils.DateTimeUtils;
import com.example.simplekhata.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.List;

public class LedgerTransactionAdapter extends RecyclerView.Adapter<LedgerTransactionAdapter.ViewHolder> {

    public interface OnLedgerTransactionClickListener {
        void onTransactionClick(LedgerTransaction transaction);
    }

    private final List<LedgerTransaction> items = new ArrayList<>();
    private final OnLedgerTransactionClickListener listener;

    public LedgerTransactionAdapter(@NonNull OnLedgerTransactionClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<LedgerTransaction> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ledger_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTypeBadge;
        private final TextView tvDateTime;
        private final TextView tvNote;
        private final TextView tvAmount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTypeBadge = itemView.findViewById(R.id.tv_tx_type_badge);
            tvDateTime = itemView.findViewById(R.id.tv_tx_date_time);
            tvNote = itemView.findViewById(R.id.tv_tx_note);
            tvAmount = itemView.findViewById(R.id.tv_tx_amount);
        }

        void bind(LedgerTransaction tx, OnLedgerTransactionClickListener listener) {
            Context context = itemView.getContext();
            tvDateTime.setText(DateTimeUtils.formatDateTimeMillis(tx.getEffectiveTimestampMillis()));

            String note = tx.getNote().trim();
            if (note.isEmpty()) {
                tvNote.setVisibility(View.GONE);
            } else {
                tvNote.setVisibility(View.VISIBLE);
                tvNote.setText(note);
            }

            String formattedAmount = MoneyUtils.formatAbsoluteRupees(tx.getAmount());
            if (tx.getTransactionType() == TransactionType.PAYMENT) {
                tvTypeBadge.setText(R.string.type_payment_badge);
                tvTypeBadge.setBackgroundResource(R.drawable.bg_badge_green);
                tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.positive_amount));
                tvAmount.setText("+ " + formattedAmount);
                tvAmount.setTextColor(ContextCompat.getColor(context, R.color.positive_amount));
            } else {
                tvTypeBadge.setText(R.string.type_credit_badge);
                tvTypeBadge.setBackgroundResource(R.drawable.bg_badge_red);
                tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.negative_amount));
                tvAmount.setText("- " + formattedAmount);
                tvAmount.setTextColor(ContextCompat.getColor(context, R.color.negative_amount));
            }

            itemView.setOnClickListener(v -> listener.onTransactionClick(tx));
        }
    }
}
