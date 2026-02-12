package com.example.pdfreader;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Main activity that manages multiple PDF viewer fragments and tab navigation.
 * Handles file selection, permissions, and fragment lifecycle.
 */
public class MainActivity extends AppCompatActivity {

    private static final String STATE_DOCUMENTS = "state_documents";
    private static final String STATE_CURRENT_INDEX = "state_current_index";
    private static final String PREFS_NAME = "pdf_reader_prefs";
    private static final String PREFS_DOCUMENTS = "saved_documents";
    private static final String PREFS_CURRENT_INDEX = "saved_current_index";
    private static final int REQUEST_PERMISSION_CODE = 100;

    private FloatingActionButton fabAddPdf;
    private ImageButton pdfMenuButton;
    private androidx.appcompat.widget.Toolbar toolbar;
    private PopupWindow floatingPanel;
    
    private final List<PdfDocument> openedDocuments = new ArrayList<>();
    private int currentDocumentIndex = -1;

    // Activity result launcher for file picker
    private final ActivityResultLauncher<Intent> filePickerLauncher = 
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Uri uri = result.getData().getData();
            if (uri != null) {
                // Take persistable URI permission
                takePersistableUriPermission(uri);
                openPdfDocument(uri);
            }
        }
    });

    // Permission request launcher
    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        if (isGranted) {
            launchFilePicker();
        } else {
            Toast.makeText(this, "Storage permission denied. Using document picker instead.", 
                    Toast.LENGTH_SHORT).show();
            launchFilePicker(); // Still works with ACTION_OPEN_DOCUMENT
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Disable the default ActionBar since we have a custom toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initializeViews();

        // Restore state or handle intent
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            // Try to restore from SharedPreferences first (persistent across app restarts)
            restorePersistedState();
        }
        
        // Ensure toolbar is hidden if PDFs are already open
        updateEmptyState();
    }
    
    /**
     * Public method to get opened documents (for HomeFragment)
     */
    public List<PdfDocument> getOpenedDocuments() {
        return openedDocuments;
    }

    private void initializeViews() {
        try {
            fabAddPdf = findViewById(R.id.fabAddPdf);
            pdfMenuButton = findViewById(R.id.pdfMenuButton);
            toolbar = findViewById(R.id.toolbar);

            if (fabAddPdf == null || pdfMenuButton == null || toolbar == null) {
                throw new NullPointerException("One or more views not found");
            }

            fabAddPdf.setOnClickListener(v -> showPdfMenu());
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing views: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show dialog with list of opened PDFs with close buttons
     */
    private void showPdfMenu() {
        if (openedDocuments.isEmpty()) {
            // If no PDFs open, just open file picker
            checkPermissionAndOpenFile();
            return;
        }
        
        // Close existing panel if any
        if (floatingPanel != null && floatingPanel.isShowing()) {
            floatingPanel.dismiss();
        }
        
        // Create custom view with RecyclerView
        View panelView = LayoutInflater.from(this).inflate(R.layout.floating_pdf_panel, null);
        RecyclerView recyclerView = panelView.findViewById(R.id.floatingPdfListRecyclerView);
        
        // Declare adapter as final so it can be used in the listener
        final PdfListAdapter[] adapterHolder = new PdfListAdapter[1];
        
        // Setup RecyclerView with adapter
        adapterHolder[0] = new PdfListAdapter(openedDocuments, currentDocumentIndex, new PdfListAdapter.OnPdfActionListener() {
            @Override
            public void onPdfSelected(int position) {
                switchToDocument(position);
                // Close floating panel when document selected
                if (floatingPanel != null && floatingPanel.isShowing()) {
                    floatingPanel.dismiss();
                }
            }

            @Override
            public void onPdfClosed(int position) {
                closeDocument(position);
                // Refresh adapter to remove item from list
                adapterHolder[0].notifyItemRemoved(position);
                adapterHolder[0].notifyItemRangeChanged(position, openedDocuments.size());
                
                // If all documents closed, close the panel
                if (openedDocuments.isEmpty()) {
                    if (floatingPanel != null && floatingPanel.isShowing()) {
                        floatingPanel.dismiss();
                    }
                }
            }
        });
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapterHolder[0]);
        
        // Setup button
        android.widget.Button openButton = panelView.findViewById(R.id.floatingOpenAnotherPdfButton);
        openButton.setOnClickListener(v -> {
            floatingPanel.dismiss();
            checkPermissionAndOpenFile();
        });
        
        // Create PopupWindow
        floatingPanel = new PopupWindow(
            panelView,
            450,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        );
        floatingPanel.setElevation(8);
        floatingPanel.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        
        // Show near FAB (top-right area)
        floatingPanel.showAsDropDown(fabAddPdf, -450 + fabAddPdf.getWidth(), 0);
    }

    /**
     * Check permissions and open file picker
     */
    private void checkPermissionAndOpenFile() {
        // For Android 13+ (API 33+), we don't need READ_EXTERNAL_STORAGE for ACTION_OPEN_DOCUMENT
        // For lower versions, check permission
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                return;
            }
        }
        
        launchFilePicker();
    }

    /**
     * Launch file picker using ACTION_OPEN_DOCUMENT (no permission required)
     */
    private void launchFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        filePickerLauncher.launch(intent);
    }

    /**
     * Take persistable URI permission for the selected file
     */
    private void takePersistableUriPermission(Uri uri) {
        try {
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (SecurityException e) {
            // Some URIs don't support persistable permissions, that's okay
            e.printStackTrace();
        }
    }

    /**
     * Open a PDF document from URI
     */
    private void openPdfDocument(Uri uri) {
        // Check if already opened
        for (PdfDocument doc : openedDocuments) {
            if (doc.getUri().equals(uri)) {
                int index = openedDocuments.indexOf(doc);
                switchToDocument(index);
                Toast.makeText(this, "PDF already opened", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Get filename from URI
        String filename = getFileNameFromUri(uri);
        
        // Create PdfDocument model
        PdfDocument document = new PdfDocument(uri, filename);
        openedDocuments.add(document);
        
        // Switch to new document
        int newIndex = openedDocuments.size() - 1;
        switchToDocument(newIndex);
        
        // Show/hide empty state
        updateEmptyState();
        
        // Save to SharedPreferences
        savePersistedState();
    }

    /**
     * Switch to a different document
     */
    private void switchToDocument(int index) {
        if (index < 0 || index >= openedDocuments.size()) return;
        
        currentDocumentIndex = index;
        PdfDocument document = openedDocuments.get(index);
        
        // Show fragment
        showPdfFragment(document);
    }

    /**
     * Close a document
     */
    private void closeDocument(int position) {
        if (position < 0 || position >= openedDocuments.size()) return;
        
        PdfDocument document = openedDocuments.get(position);
        
        // Remove fragment
        String tag = getFragmentTag(document.getUri());
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(fragment)
                    .commit();
        }
        
        // Remove from list
        openedDocuments.remove(position);
        
        // Update current index
        if (currentDocumentIndex == position) {
            // Switch to previous or next document
            if (openedDocuments.isEmpty()) {
                currentDocumentIndex = -1;
            } else if (position > 0) {
                switchToDocument(position - 1);
            } else {
                switchToDocument(0);
            }
        } else if (currentDocumentIndex > position) {
            currentDocumentIndex--;
        }
        
        updateEmptyState();
        
        // Save to SharedPreferences
        savePersistedState();
    }

    /**
     * Show PDF fragment for document
     */
    private void showPdfFragment(PdfDocument document) {
        String tag = getFragmentTag(document.getUri());
        FragmentManager fm = getSupportFragmentManager();
        
        // Hide all fragments first
        FragmentTransaction transaction = fm.beginTransaction();
        for (PdfDocument doc : openedDocuments) {
            String docTag = getFragmentTag(doc.getUri());
            Fragment fragment = fm.findFragmentByTag(docTag);
            if (fragment != null) {
                transaction.hide(fragment);
            }
        }
        
        // Find or create fragment
        PdfViewerFragment fragment = (PdfViewerFragment) fm.findFragmentByTag(tag);
        if (fragment == null) {
            // Create new fragment
            fragment = PdfViewerFragment.newInstance(document.getUri(), document.getDisplayName());
            transaction.add(R.id.fragmentContainer, fragment, tag);
        } else {
            // Show existing fragment
            transaction.show(fragment);
        }
        
        transaction.commit();
    }

    /**
     * Generate fragment tag from URI
     */
    private String getFragmentTag(Uri uri) {
        return "pdf_fragment_" + uri.toString().hashCode();
    }

    /**
     * Get filename from URI using ContentResolver
     */
    private String getFileNameFromUri(Uri uri) {
        String filename = "document.pdf";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    filename = cursor.getString(nameIndex);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return filename;
    }

    /**
     * Update empty state visibility
     */
    private void updateEmptyState() {
        try {
            View emptyState = findViewById(R.id.emptyStateLayout);
            if (emptyState != null) {
                emptyState.setVisibility(openedDocuments.isEmpty() ? View.VISIBLE : View.GONE);
            }
            
            // Show toolbar only when no PDFs are open
            if (toolbar != null) {
                toolbar.setVisibility(openedDocuments.isEmpty() ? View.VISIBLE : View.GONE);
            }
            
            // Move FAB based on whether PDFs are open and set opacity
            if (fabAddPdf != null) {
                if (openedDocuments.isEmpty()) {
                    // Bottom right when no PDFs - full opacity
                    moveFabToBottomRight();
                    fabAddPdf.setAlpha(1.0f);
                } else {
                    // Top right when PDFs are open - 25% opacity by default
                    moveFabToTopRight();
                    fabAddPdf.setAlpha(0.25f);
                }
            }
            
            // Toggle full screen mode
            if (openedDocuments.isEmpty()) {
                // Show status bar
                showStatusBar();
            } else {
                // Hide status bar for full screen PDF reading
                hideStatusBar();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Move FAB to bottom right
     */
    private void moveFabToBottomRight() {
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) fabAddPdf.getLayoutParams();
        params.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
        params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
        params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        fabAddPdf.setLayoutParams(params);
    }

    /**
     * Move FAB to top right
     */
    private void moveFabToTopRight() {
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) fabAddPdf.getLayoutParams();
        params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
        params.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
        params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        fabAddPdf.setLayoutParams(params);
    }

    /**
     * Hide status bar and navigation bar for full screen reading
     */
    private void hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (insetsController != null) {
                insetsController.hide(WindowInsetsCompat.Type.statusBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    /**
     * Show status bar and navigation bar
     */
    private void showStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (insetsController != null) {
                insetsController.show(WindowInsetsCompat.Type.statusBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    /**
     * Hide the FAB
     */
    public void hideFab() {
        if (fabAddPdf != null) {
            fabAddPdf.hide();
        }
    }

    /**
     * Show the FAB
     */
    public void showFab() {
        if (fabAddPdf != null && !openedDocuments.isEmpty()) {
            fabAddPdf.show();
        }
    }

    /**
     * Set FAB opacity
     */
    public void setFabOpacity(float opacity) {
        if (fabAddPdf != null) {
            fabAddPdf.setAlpha(opacity);
        }
    }

    /**
     * Callback from fragment when PDF is loaded
     */
    public void onPdfLoaded(Uri uri, int pageCount) {
        for (PdfDocument doc : openedDocuments) {
            if (doc.getUri().equals(uri)) {
                doc.setPageCount(pageCount);
                break;
            }
        }
    }

    /**
     * Handle incoming intent (e.g., open PDF from file manager)
     */
    private void handleIntent(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            Uri uri = intent.getData();
            takePersistableUriPermission(uri);
            openPdfDocument(uri);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        
        // Save opened documents and current index
        outState.putParcelableArrayList(STATE_DOCUMENTS, new ArrayList<>(openedDocuments));
        outState.putInt(STATE_CURRENT_INDEX, currentDocumentIndex);
    }

    /**
     * Restore state after configuration change
     */
    private void restoreState(Bundle savedInstanceState) {
        ArrayList<PdfDocument> documents = savedInstanceState.getParcelableArrayList(STATE_DOCUMENTS);
        int index = savedInstanceState.getInt(STATE_CURRENT_INDEX, -1);
        
        if (documents != null && !documents.isEmpty()) {
            openedDocuments.clear();
            openedDocuments.addAll(documents);
            
            if (index >= 0 && index < openedDocuments.size()) {
                switchToDocument(index);
            }
        }
        
        updateEmptyState();
    }
    
    /**
     * Save opened documents to SharedPreferences for persistence across app restarts
     */
    private void savePersistedState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Convert document URIs to strings for storage
        StringBuilder uriList = new StringBuilder();
        for (int i = 0; i < openedDocuments.size(); i++) {
            if (i > 0) uriList.append("|");
            uriList.append(openedDocuments.get(i).getUri().toString());
        }
        
        editor.putString(PREFS_DOCUMENTS, uriList.toString());
        editor.putInt(PREFS_CURRENT_INDEX, currentDocumentIndex);
        editor.apply();
    }
    
    /**
     * Restore opened documents from SharedPreferences
     * @return true if documents were restored, false if none were saved
     */
    private boolean restorePersistedState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String uriListStr = prefs.getString(PREFS_DOCUMENTS, "");
        
        if (uriListStr.isEmpty()) {
            return false;
        }
        
        // Parse saved URIs
        String[] uriStrings = uriListStr.split("\\|");
        boolean allUrisValid = true;
        
        for (String uriStr : uriStrings) {
            try {
                Uri uri = Uri.parse(uriStr);
                // Reclaim persistable URI permission
                takePersistableUriPermission(uri);
                // Check if file is still accessible
                if (getFileNameFromUri(uri) != null) {
                    PdfDocument document = new PdfDocument(uri, getFileNameFromUri(uri));
                    openedDocuments.add(document);
                } else {
                    allUrisValid = false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                allUrisValid = false;
            }
        }
        
        // Restore current index
        int savedIndex = prefs.getInt(PREFS_CURRENT_INDEX, -1);
        if (savedIndex >= 0 && savedIndex < openedDocuments.size()) {
            switchToDocument(savedIndex);
        } else if (!openedDocuments.isEmpty()) {
            switchToDocument(0);
        }
        
        updateEmptyState();
        
        return !openedDocuments.isEmpty();
    }
}
