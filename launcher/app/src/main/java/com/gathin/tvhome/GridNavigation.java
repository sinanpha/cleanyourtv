package com.gathin.tvhome;

/** Physical left/right boundaries for a grid whose first column follows its reading direction. */
final class GridNavigation {
    private GridNavigation() { }

    static boolean atHorizontalEdge(int position, int count, int columns, boolean rtl, boolean left) {
        boolean towardStart = left != rtl;
        if (towardStart) return position % columns == 0;
        return position % columns == columns - 1 || position == count - 1;
    }

    /** With wrap_rows: the tile at the row's other end, for a key pressed at its start (towardStart) or end. */
    static int wrapTarget(int position, int count, int columns, boolean towardStart) {
        int rowStart = (position / columns) * columns;
        int rowEnd = Math.min(rowStart + columns, count) - 1;
        return towardStart ? rowEnd : rowStart;
    }
}
