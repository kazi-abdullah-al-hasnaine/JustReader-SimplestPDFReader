package com.example.pdfreader;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class HomeFragment extends Fragment {
    private RecyclerView recentPdfRecyclerView;
    private LinearLayout emptyStateLayout;
    private LinearLayout recentPdfsLayout;
    private RecentPdfAdapter adapter;
    private OnPdfSelectedListener listener;
    private OnOpenPdfListener openPdfListener;

    public interface OnPdfSelectedListener {
        void onRecentPdfSelected(PdfDocument document);
    }
    
    public interface OnOpenPdfListener {
        void onOpenPdfClicked();
    }

    public HomeFragment(List<PdfDocument> recentPdfs, OnPdfSelectedListener listener, OnOpenPdfListener openPdfListener) {
        this.listener = listener;
        this.openPdfListener = openPdfListener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        recentPdfRecyclerView = view.findViewById(R.id.recentPdfRecyclerView);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        recentPdfsLayout = view.findViewById(R.id.recentPdfsLayout);
        Button openPdfButtonEmpty = view.findViewById(R.id.openPdfButtonEmpty);
        
        // Get recent PDFs from activity
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            List<PdfDocument> recentPdfs = activity.getOpenedDocuments();
            
            if (recentPdfs.isEmpty()) {
                // Show empty state
                emptyStateLayout.setVisibility(View.VISIBLE);
                recentPdfsLayout.setVisibility(View.GONE);
                
                // Open PDF button click
                openPdfButtonEmpty.setOnClickListener(v -> {
                    if (openPdfListener != null) {
                        openPdfListener.onOpenPdfClicked();
                    }
                });
            } else {
                // Show recent PDFs
                emptyStateLayout.setVisibility(View.GONE);
                recentPdfsLayout.setVisibility(View.VISIBLE);
                
                // Setup adapter
                adapter = new RecentPdfAdapter(recentPdfs, position -> {
                    if (listener != null) {
                        listener.onRecentPdfSelected(recentPdfs.get(position));
                    }
                });
                
                // Use GridLayoutManager for a nice grid layout
                recentPdfRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
                recentPdfRecyclerView.setAdapter(adapter);
            }
        }
    }
}
