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
import com.example.simplekhata.models.RecentActivityItem;
import com.example.simplekhata.models.TransactionType;
import com.example.simplekhata.utils.DateTimeUtils;
import com.example.simplekhata.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.List;

public class RecentTransactionAdapter extends RecyclerView.Adapter<RecentTransactionAdapter.ViewHolder> {

    public interface OnRecentTransactionClickListener {
        void onTransactionClick(RecentActivityItem item);
    }

    private final List<RecentActivityItem> items = new ArrayList<>();
    private final OnRecentTransactionClickListener listener;

    public RecentTransactionAdapter(@NonNull OnRecentTransactionClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<RecentActivityItem> newItems) {
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
                .inflate(R.layout.item_recent_transaction, parent, false);
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
        private final TextView tvCustomerName;
        private final TextView tvTypeBadge;
        private final TextView tvDate;
        private final TextView tvAmount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCustomerName = itemView.findViewById(R.id.tv_recent_customer_name);
            tvTypeBadge = itemView.findViewById(R.id.tv_recent_type_badge);
            tvDate = itemView.findViewById(R.id.tv_recent_date);
            tvAmount = itemView.findViewById(R.id.tv_recent_amount);
        }

        void bind(RecentActivityItem item, OnRecentTransactionClickListener listener) {
            Context context = itemView.getContext();
            LedgerTransaction tx = item.getTransaction();
            tvCustomerName.setText(item.getCustomerName());
            tvDate.setText(DateTimeUtils.formatDateMillis(tx.getEffectiveTimestampMillis()));

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

            itemView.setOnClickListener(v -> listener.onTransactionClick(item));
        }
    }
}
