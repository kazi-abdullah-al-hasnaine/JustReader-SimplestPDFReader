package com.example.pdfreader;

import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;

/**
 * Fragment that displays a single PDF document using PdfRenderer.
 * Handles orientation changes gracefully and manages PdfRenderer lifecycle.
 */
public class PdfViewerFragment extends Fragment {

    private static final String ARG_PDF_URI = "pdf_uri";
    private static final String ARG_PDF_NAME = "pdf_name";
    private static final String STATE_SCROLL_POSITION = "scroll_position";

    private RecyclerView recyclerView;
    private PdfPageAdapter adapter;
    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor fileDescriptor;
    private LinearLayoutManager layoutManager;
    private Handler fadeHandler;

    private Uri pdfUri;
    private String pdfName;
    private int savedScrollPosition = 0;

    /**
     * Factory method to create new instance with arguments
     */
    public static PdfViewerFragment newInstance(Uri pdfUri, String pdfName) {
        PdfViewerFragment fragment = new PdfViewerFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_PDF_URI, pdfUri);
        args.putString(ARG_PDF_NAME, pdfName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Retrieve arguments
        if (getArguments() != null) {
            pdfUri = getArguments().getParcelable(ARG_PDF_URI);
            pdfName = getArguments().getString(ARG_PDF_NAME);
        }

        // Restore scroll position if exists
        if (savedInstanceState != null) {
            savedScrollPosition = savedInstanceState.getInt(STATE_SCROLL_POSITION, 0);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pdf_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        fadeHandler = new Handler(Looper.getMainLooper());

        // Initialize RecyclerView
        recyclerView = view.findViewById(R.id.pdfRecyclerView);
        layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(false);

        // Add scroll listener to fade FAB while scrolling
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                
                if (getActivity() instanceof MainActivity) {
                    MainActivity activity = (MainActivity) getActivity();
                    
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        // User is scrolling - fade FAB to 25% opacity
                        activity.setFabOpacity(0.25f);
                        fadeHandler.removeCallbacks(fadeFabRunnable);
                    }
                }
            }
        });

        // Add tap listener to main view to show FAB at full opacity on single tap
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (getActivity() instanceof MainActivity) {
                    MainActivity activity = (MainActivity) getActivity();
                    activity.setFabOpacity(1.0f); // Full opacity
                    
                    // Fade back to 25% after 2 seconds of inactivity
                    fadeHandler.removeCallbacks(fadeFabRunnable);
                    fadeHandler.postDelayed(fadeFabRunnable, 2000);
                }
            }
            return false;
        });

        // Open PDF and setup adapter
        if (pdfUri != null) {
            openPdfRenderer();
        }
    }

    private final Runnable fadeFabRunnable = () -> {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setFabOpacity(0.25f);
        }
    };

    /**
     * Opens the PDF using PdfRenderer
     */
    private void openPdfRenderer() {
        try {
            // Open file descriptor from URI
            fileDescriptor = requireContext().getContentResolver()
                    .openFileDescriptor(pdfUri, "r");

            if (fileDescriptor != null) {
                // Create PdfRenderer
                pdfRenderer = new PdfRenderer(fileDescriptor);

                // Create and set adapter
                adapter = new PdfPageAdapter(pdfRenderer);
                recyclerView.setAdapter(adapter);

                // Restore scroll position
                if (savedScrollPosition > 0) {
                    layoutManager.scrollToPosition(savedScrollPosition);
                }

                // Notify parent activity of page count
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).onPdfLoaded(pdfUri, pdfRenderer.getPageCount());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to open PDF: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Closes the PdfRenderer and releases resources
     */
    private void closePdfRenderer() {
        if (adapter != null) {
            adapter.release();
            adapter = null;
        }

        if (pdfRenderer != null) {
            pdfRenderer.close();
            pdfRenderer = null;
        }

        if (fileDescriptor != null) {
            try {
                fileDescriptor.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            fileDescriptor = null;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        
        // Save current scroll position
        if (layoutManager != null) {
            int position = layoutManager.findFirstVisibleItemPosition();
            outState.putInt(STATE_SCROLL_POSITION, position);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Save scroll position before view is destroyed
        if (layoutManager != null) {
            savedScrollPosition = layoutManager.findFirstVisibleItemPosition();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up PdfRenderer resources
        closePdfRenderer();
    }

    /**
     * Get the URI of the PDF being displayed
     */
    public Uri getPdfUri() {
        return pdfUri;
    }

    /**
     * Get the display name of the PDF
     */
    public String getPdfName() {
        return pdfName;
    }

    /**
     * Scroll to a specific page
     */
    public void scrollToPage(int pageIndex) {
        if (layoutManager != null && pageIndex >= 0) {
            layoutManager.scrollToPositionWithOffset(pageIndex, 0);
        }
    }

    /**
     * Get current page number
     */
    public int getCurrentPage() {
        if (layoutManager != null) {
            return layoutManager.findFirstVisibleItemPosition();
        }
        return 0;
    }
}
