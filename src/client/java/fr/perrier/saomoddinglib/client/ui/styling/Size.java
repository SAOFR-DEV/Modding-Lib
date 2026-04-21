package fr.perrier.saomoddinglib.client.ui.styling;

public class Size {
    public static final int WRAP_CONTENT = -1;
    public static final int MATCH_PARENT = -2;

    public static boolean isWrapContent(int size) {
        return size == WRAP_CONTENT;
    }

    public static boolean isMatchParent(int size) {
        return size == MATCH_PARENT;
    }

    public static boolean isFixed(int size) {
        return size > 0;
    }
}

