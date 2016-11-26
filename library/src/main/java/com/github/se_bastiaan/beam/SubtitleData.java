package com.github.se_bastiaan.beam;

import android.support.annotation.NonNull;

public class SubtitleData {

    private final String url;
    private final String mimeType;
    private final String label;
    private final String language;

    public static class Builder {
        // required fields
        private String url;

        // optional fields
        private String mimeType;
        private String label;
        private String language;

        public Builder(@NonNull String url) {
            this.url = url;
        }

        public Builder setMimeType(@NonNull String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public Builder setLabel(@NonNull String label) {
            this.label = label;
            return this;
        }

        public Builder setLanguage(@NonNull String language) {
            this.language = language;
            return this;
        }

        public SubtitleData build() {
            return new SubtitleData(this);
        }
    }

    private SubtitleData(SubtitleData.Builder builder) {
        url = builder.url;
        mimeType = builder.mimeType;
        label = builder.label;
        language = builder.language;
    }

    public String getUrl() {
        return url;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getLabel() {
        return label;
    }

    public String getLanguage() {
        return language;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SubtitleData that = (SubtitleData) o;

        if (getUrl() != null ? !getUrl().equals(that.getUrl()) : that.getUrl() != null) {
            return false;
        }
        return !(getMimeType() != null ? !getMimeType().equals(that.getMimeType()) : that.getMimeType() != null);

    }

    @Override
    public int hashCode() {
        int result = getUrl() != null ? getUrl().hashCode() : 0;
        result = 31 * result + (getMimeType() != null ? getMimeType().hashCode() : 0);
        return result;
    }

}
