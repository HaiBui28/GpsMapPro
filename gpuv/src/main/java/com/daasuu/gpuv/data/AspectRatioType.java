package com.daasuu.gpuv.data;

public enum AspectRatioType {
    RATIO_4_3(4f / 3f),
    RATIO_16_9(16f / 9f);

    private final float ratio;

    AspectRatioType(float ratio) {
        this.ratio = ratio;
    }

    public float getRatio() {
        return ratio;
    }
}
