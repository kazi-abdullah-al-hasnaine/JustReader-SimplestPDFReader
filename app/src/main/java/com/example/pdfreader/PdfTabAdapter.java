package com.example.pdfreader;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Adapter for horizontal tab RecyclerView showing opened PDF documents.
 */
public class PdfTabAdapter extends RecyclerView.Adapter<PdfTabAdapter.TabViewHolder> {

    private final List<PdfDocument> documents;
    private final TabClickListener listener;
    private int selectedPosition = -1;

    public interface TabClickListener {
        void onTabClick(int position);
        void onTabClose(int position);
    }

    public PdfTabAdapter(List<PdfDocument> documents, TabClickListener listener) {
        this.documents = documents;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pdf_tab, parent, false);
        return new TabViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TabViewHolder holder, int position) {
        holder.bind(documents.get(position), position);
    }

    @Override
    public int getItemCount() {
        return documents.size();
    }

    public void setSelectedPosition(int position) {
        int previousSelected = selectedPosition;
        selectedPosition = position;
        
        if (previousSelected >= 0 && previousSelected < documents.size()) {
            notifyItemChanged(previousSelected);
        }
        if (selectedPosition >= 0 && selectedPosition < documents.size()) {
            notifyItemChanged(selectedPosition);
        }
    }

    class TabViewHolder extends RecyclerView.ViewHolder {
        private final CardView cardView;
        private final TextView titleText;
        private final TextView pageCountText;
        private final ImageButton closeButton;

        TabViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.tabCardView);
            titleText = itemView.findViewById(R.id.tabTitleText);
            pageCountText = itemView.findViewById(R.id.tabPageCountText);
            closeButton = itemView.findViewById(R.id.tabCloseButton);
        }

        void bind(PdfDocument document, int position) {
            titleText.setText(document.getDisplayName());
            
            // Show page count if available
            if (document.getPageCount() > 0) {
                pageCountText.setText(String.format("%d pages", document.getPageCount()));
                pageCountText.setVisibility(View.VISIBLE);
            } else {
                pageCountText.setVisibility(View.GONE);
            }

            // Highlight selected tab
            boolean isSelected = position == selectedPosition;
            cardView.setCardElevation(isSelected ? 8f : 2f);
            itemView.setAlpha(isSelected ? 1.0f : 0.7f);

            // Click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTabClick(position);
                }
            });

            closeButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTabClose(position);
                }
            });
        }
    }
}
