package com.example.pdfreader;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Model class representing an opened PDF document.
 * Implements Parcelable for efficient state saving across configuration changes.
 */
public class PdfDocument implements Parcelable {
    private final Uri uri;
    private final String displayName;
    private final String id; // Unique identifier
    private int pageCount;
    private int currentPage;

    public PdfDocument(Uri uri, String displayName) {
        this.uri = uri;
        this.displayName = displayName;
        this.id = uri.toString(); // Use URI as unique ID
        this.pageCount = 0;
        this.currentPage = 0;
    }

    protected PdfDocument(Parcel in) {
        uri = in.readParcelable(Uri.class.getClassLoader());
        displayName = in.readString();
        id = in.readString();
        pageCount = in.readInt();
        currentPage = in.readInt();
    }

    public static final Creator<PdfDocument> CREATOR = new Creator<PdfDocument>() {
        @Override
        public PdfDocument createFromParcel(Parcel in) {
            return new PdfDocument(in);
        }

        @Override
        public PdfDocument[] newArray(int size) {
            return new PdfDocument[size];
        }
    };

    public Uri getUri() {
        return uri;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getId() {
        return id;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(uri, flags);
        dest.writeString(displayName);
        dest.writeString(id);
        dest.writeInt(pageCount);
        dest.writeInt(currentPage);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PdfDocument that = (PdfDocument) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
