package org.triggersstudio.moddinglib.client.ui.styling;

import org.triggersstudio.moddinglib.client.ui.events.ClickHandler;

/**
 * Immutable style configuration for UI components.
 * Use static methods or chain methods to build styles.
 */
public class Style {
    private final int width;
    private final int height;
    private final Insets padding;
    private final Insets margin;
    private final int backgroundColor;
    private final int textColor;
    private final float fontSize;
    private final int borderRadius;
    private final int borderColor;
    private final int borderWidth;
    private final Alignment horizontalAlignment;
    private final Alignment verticalAlignment;
    private final ClickHandler clickHandler;
    private final float opacity;
    private final boolean bold;
    private final int placeholderColor;
    private final ObjectFit objectFit;

    private Style(Builder builder) {
        this.width = builder.width;
        this.height = builder.height;
        this.padding = builder.padding;
        this.margin = builder.margin;
        this.backgroundColor = builder.backgroundColor;
        this.textColor = builder.textColor;
        this.fontSize = builder.fontSize;
        this.borderRadius = builder.borderRadius;
        this.borderColor = builder.borderColor;
        this.borderWidth = builder.borderWidth;
        this.horizontalAlignment = builder.horizontalAlignment;
        this.verticalAlignment = builder.verticalAlignment;
        this.clickHandler = builder.clickHandler;
        this.opacity = builder.opacity;
        this.bold = builder.bold;
        this.placeholderColor = builder.placeholderColor;
        this.objectFit = builder.objectFit;
    }

    // Getters
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Insets getPadding() {
        return padding;
    }

    public Insets getMargin() {
        return margin;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public int getTextColor() {
        return textColor;
    }

    public float getFontSize() {
        return fontSize;
    }

    public int getBorderRadius() {
        return borderRadius;
    }

    public int getBorderColor() {
        return borderColor;
    }

    public int getBorderWidth() {
        return borderWidth;
    }

    public Alignment getHorizontalAlignment() {
        return horizontalAlignment;
    }

    public Alignment getVerticalAlignment() {
        return verticalAlignment;
    }

    public ClickHandler getClickHandler() {
        return clickHandler;
    }

    public float getOpacity() {
        return opacity;
    }

    public boolean isBold() {
        return bold;
    }

    /**
     * @return the explicit placeholder color, or {@code 0} when unset — in
     * which case consumers should derive from {@link #getTextColor()} at
     * reduced alpha.
     */
    public int getPlaceholderColor() {
        return placeholderColor;
    }

    /**
     * @return the requested fit mode for aspect-ratio'd children (video,
     * image), or {@code null} when unset — in which case consumers pick
     * their own sensible default (e.g. {@code VideoComponent} uses
     * {@link ObjectFit#CONTAIN}).
     */
    public ObjectFit getObjectFit() {
        return objectFit;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.width = this.width;
        builder.height = this.height;
        builder.padding = this.padding;
        builder.margin = this.margin;
        builder.backgroundColor = this.backgroundColor;
        builder.textColor = this.textColor;
        builder.fontSize = this.fontSize;
        builder.borderRadius = this.borderRadius;
        builder.borderColor = this.borderColor;
        builder.borderWidth = this.borderWidth;
        builder.horizontalAlignment = this.horizontalAlignment;
        builder.verticalAlignment = this.verticalAlignment;
        builder.clickHandler = this.clickHandler;
        builder.opacity = this.opacity;
        builder.bold = this.bold;
        builder.placeholderColor = this.placeholderColor;
        builder.objectFit = this.objectFit;
        return builder;
    }

    // Static factory methods for fluent API
    public static Builder width(int width) {
        return builder().width(width);
    }

    public static Builder height(int height) {
        return builder().height(height);
    }

    public static Builder padding(int all) {
        return builder().padding(all);
    }

    public static Builder padding(int vertical, int horizontal) {
        return builder().padding(vertical, horizontal);
    }

    public static Builder padding(int top, int right, int bottom, int left) {
        return builder().padding(top, right, bottom, left);
    }

    public static Builder margin(int all) {
        return builder().margin(all);
    }

    public static Builder margin(int vertical, int horizontal) {
        return builder().margin(vertical, horizontal);
    }

    public static Builder margin(int top, int right, int bottom, int left) {
        return builder().margin(top, right, bottom, left);
    }

    public static Builder backgroundColor(int color) {
        return builder().backgroundColor(color);
    }

    public static Builder textColor(int color) {
        return builder().textColor(color);
    }

    public static Builder fontSize(float size) {
        return builder().fontSize(size);
    }

    public static Builder borderRadius(int radius) {
        return builder().borderRadius(radius);
    }

    public static Builder border(int color, int width) {
        return builder().border(color, width);
    }

    public static Builder align(Alignment horizontal, Alignment vertical) {
        return builder().align(horizontal, vertical);
    }

    public static Builder onClick(ClickHandler handler) {
        return builder().onClick(handler);
    }

    public static Builder opacity(float opacity) {
        return builder().opacity(opacity);
    }

    public static Builder bold() {
        return builder().bold();
    }

    public static Builder placeholderColor(int color) {
        return builder().placeholderColor(color);
    }

    public static Builder objectFit(ObjectFit fit) {
        return builder().objectFit(fit);
    }

    // Builder class
    public static class Builder {
        private int width = Size.WRAP_CONTENT;
        private int height = Size.WRAP_CONTENT;
        private Insets padding = Insets.ZERO;
        private Insets margin = Insets.ZERO;
        private int backgroundColor = 0x00_00_00_00;
        private int textColor = 0xFF_FF_FF_FF;
        private float fontSize = 9f;
        private int borderRadius = 0;
        private int borderColor = 0xFF_00_00_00;
        private int borderWidth = 0;
        private Alignment horizontalAlignment = Alignment.START;
        private Alignment verticalAlignment = Alignment.START;
        private ClickHandler clickHandler = null;
        private float opacity = 1.0f;
        private boolean bold = false;
        private int placeholderColor = 0; // 0 ⇒ derive from textColor
        private ObjectFit objectFit = null; // null ⇒ consumer-defined default

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder padding(int all) {
            this.padding = new Insets(all);
            return this;
        }

        public Builder padding(int vertical, int horizontal) {
            this.padding = new Insets(vertical, horizontal);
            return this;
        }

        public Builder padding(int top, int right, int bottom, int left) {
            this.padding = new Insets(top, right, bottom, left);
            return this;
        }

        public Builder margin(int all) {
            this.margin = new Insets(all);
            return this;
        }

        public Builder margin(int vertical, int horizontal) {
            this.margin = new Insets(vertical, horizontal);
            return this;
        }

        public Builder margin(int top, int right, int bottom, int left) {
            this.margin = new Insets(top, right, bottom, left);
            return this;
        }

        public Builder backgroundColor(int color) {
            this.backgroundColor = color;
            return this;
        }

        public Builder textColor(int color) {
            this.textColor = color;
            return this;
        }

        public Builder fontSize(float size) {
            this.fontSize = size;
            return this;
        }

        public Builder borderRadius(int radius) {
            this.borderRadius = radius;
            return this;
        }

        public Builder border(int color, int width) {
            this.borderColor = color;
            this.borderWidth = width;
            return this;
        }

        public Builder align(Alignment horizontal, Alignment vertical) {
            this.horizontalAlignment = horizontal;
            this.verticalAlignment = vertical;
            return this;
        }

        public Builder onClick(ClickHandler handler) {
            this.clickHandler = handler;
            return this;
        }

        public Builder opacity(float opacity) {
            this.opacity = Math.max(0f, Math.min(1f, opacity));
            return this;
        }

        public Builder bold() {
            this.bold = true;
            return this;
        }

        public Builder placeholderColor(int color) {
            this.placeholderColor = color;
            return this;
        }

        /**
         * How an aspect-ratio'd child (video, image) should be fitted into
         * the component bounds. See {@link ObjectFit} for the four modes.
         * Passing {@code null} reverts to the consumer's default
         * ({@code VideoComponent} treats null as {@link ObjectFit#CONTAIN}).
         */
        public Builder objectFit(ObjectFit fit) {
            this.objectFit = fit;
            return this;
        }

        public Style build() {
            return new Style(this);
        }
    }

    // Default style constant
    public static final Style DEFAULT = builder().build();
}

