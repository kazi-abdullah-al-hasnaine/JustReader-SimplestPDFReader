package com.example.pdfreader;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RecyclerView Adapter for rendering PDF pages efficiently.
 * Uses background thread for bitmap rendering to avoid blocking UI.
 */
public class PdfPageAdapter extends RecyclerView.Adapter<PdfPageAdapter.PageViewHolder> {

    private final PdfRenderer pdfRenderer;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public PdfPageAdapter(PdfRenderer pdfRenderer) {
        this.pdfRenderer = pdfRenderer;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pdf_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return pdfRenderer != null ? pdfRenderer.getPageCount() : 0;
    }

    /**
     * Clean up resources when adapter is destroyed
     */
    public void release() {
        executorService.shutdown();
    }

    class PageViewHolder extends RecyclerView.ViewHolder {
        private final ImageView pageImageView;
        private final ProgressBar progressBar;
        private final TextView pageNumberText;
        private Bitmap currentBitmap;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            pageImageView = itemView.findViewById(R.id.pageImageView);
            progressBar = itemView.findViewById(R.id.progressBar);
            pageNumberText = itemView.findViewById(R.id.pageNumberText);
        }

        void bind(int pageIndex) {
            // Show loading state
            progressBar.setVisibility(View.VISIBLE);
            pageImageView.setVisibility(View.GONE);
            pageNumberText.setText(String.format("Page %d", pageIndex + 1));

            // Recycle previous bitmap if exists
            if (currentBitmap != null && !currentBitmap.isRecycled()) {
                currentBitmap.recycle();
                currentBitmap = null;
            }

            // Render page on background thread
            executorService.execute(() -> {
                if (pdfRenderer == null) return;

                try {
                    // Open the page
                    PdfRenderer.Page page = pdfRenderer.openPage(pageIndex);

                    // Calculate bitmap dimensions
                    // Use screen density for optimal quality/performance balance
                    float scale = itemView.getContext().getResources().getDisplayMetrics().density;
                    int width = (int) (page.getWidth() * scale);
                    int height = (int) (page.getHeight() * scale);

                    // Create bitmap for rendering
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

                    // Render PDF page to bitmap
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                    // Close the page
                    page.close();

                    // Update UI on main thread
                    final Bitmap finalBitmap = bitmap;
                    mainHandler.post(() -> {
                        // Check if this ViewHolder is still bound to the same position
                        if (getBindingAdapterPosition() == pageIndex) {
                            currentBitmap = finalBitmap;
                            pageImageView.setImageBitmap(finalBitmap);
                            pageImageView.setVisibility(View.VISIBLE);
                            progressBar.setVisibility(View.GONE);
                        } else {
                            // ViewHolder has been recycled, release bitmap
                            finalBitmap.recycle();
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    mainHandler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        pageNumberText.setText(String.format("Error loading page %d", pageIndex + 1));
                    });
                }
            });
        }

        /**
         * Clean up when ViewHolder is recycled
         */
        void recycle() {
            if (currentBitmap != null && !currentBitmap.isRecycled()) {
                currentBitmap.recycle();
                currentBitmap = null;
            }
            pageImageView.setImageBitmap(null);
        }
    }

    @Override
    public void onViewRecycled(@NonNull PageViewHolder holder) {
        super.onViewRecycled(holder);
        holder.recycle();
    }
}
