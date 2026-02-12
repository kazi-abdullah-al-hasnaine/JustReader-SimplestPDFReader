package com.example.pdfreader;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PdfListAdapter extends RecyclerView.Adapter<PdfListAdapter.ViewHolder> {
    private List<PdfDocument> pdfDocuments;
    private int currentIndex;
    private OnPdfActionListener listener;

    public interface OnPdfActionListener {
        void onPdfSelected(int position);
        void onPdfClosed(int position);
    }

    public PdfListAdapter(List<PdfDocument> pdfDocuments, int currentIndex, OnPdfActionListener listener) {
        this.pdfDocuments = pdfDocuments;
        this.currentIndex = currentIndex;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pdf_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PdfDocument doc = pdfDocuments.get(position);
        String displayName = doc.getDisplayName();
        
        // Mark current PDF with checkmark
        if (position == currentIndex) {
            displayName = "✓ " + displayName;
        }
        
        holder.pdfNameText.setText(displayName);
        
        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPdfSelected(position);
            }
        });
        
        holder.closeButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPdfClosed(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return pdfDocuments.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView pdfNameText;
        ImageButton closeButton;

        ViewHolder(View itemView) {
            super(itemView);
            pdfNameText = itemView.findViewById(R.id.pdfNameText);
            closeButton = itemView.findViewById(R.id.closeButton);
        }
    }
}
