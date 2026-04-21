package fr.perrier.saomoddinglib.client.ui.styling;

public class Insets {
    public final int top;
    public final int right;
    public final int bottom;
    public final int left;

    public Insets(int all) {
        this.top = all;
        this.right = all;
        this.bottom = all;
        this.left = all;
    }

    public Insets(int vertical, int horizontal) {
        this.top = vertical;
        this.bottom = vertical;
        this.left = horizontal;
        this.right = horizontal;
    }

    public Insets(int top, int right, int bottom, int left) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
    }

    public int getHorizontal() {
        return left + right;
    }

    public int getVertical() {
        return top + bottom;
    }

    public static final Insets ZERO = new Insets(0);
}

