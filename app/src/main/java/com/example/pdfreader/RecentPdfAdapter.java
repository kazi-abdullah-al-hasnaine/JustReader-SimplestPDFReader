package com.example.pdfreader;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RecentPdfAdapter extends RecyclerView.Adapter<RecentPdfAdapter.ViewHolder> {
    private List<PdfDocument> pdfDocuments;
    private OnPdfClickListener listener;

    public interface OnPdfClickListener {
        void onPdfClicked(int position);
    }

    public RecentPdfAdapter(List<PdfDocument> pdfDocuments, OnPdfClickListener listener) {
        this.pdfDocuments = pdfDocuments;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent_pdf, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PdfDocument doc = pdfDocuments.get(position);
        holder.pdfNameText.setText(doc.getDisplayName());
        
        // Click to open PDF
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPdfClicked(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return pdfDocuments.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView pdfNameText;

        ViewHolder(View itemView) {
            super(itemView);
            pdfNameText = itemView.findViewById(R.id.recentPdfName);
        }
    }
}
