package com.ace77505.watchface.firefly;

import android.graphics.Color;

public class ColorOption {
    private final String name;
    private final int color;
    private final boolean isCustom;

    public ColorOption(String name, int color) {
        this(name, color, false);
    }

    public ColorOption(String name, int color, boolean isCustom) {
        this.name = name;
        this.color = color;
        this.isCustom = isCustom;
    }

    public String getName() {
        return name;
    }

    public int getColor() {
        return color;
    }

    public boolean isCustom() {
        return isCustom;
    }

    // 预定义颜色列表
    public static ColorOption[] getPresetColors() {
        return new ColorOption[] {
                new ColorOption("黑色", Color.BLACK),
                new ColorOption("白色", Color.WHITE),
                new ColorOption("红色", Color.RED),
                new ColorOption("蓝色", Color.BLUE),
                new ColorOption("绿色", Color.GREEN),
                new ColorOption("黄色", Color.YELLOW),
                new ColorOption("青色", Color.CYAN),
                new ColorOption("洋红", Color.MAGENTA),
                new ColorOption("自定义", Color.TRANSPARENT, true)
        };
    }
}