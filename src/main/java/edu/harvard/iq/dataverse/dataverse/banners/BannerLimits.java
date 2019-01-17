package edu.harvard.iq.dataverse.dataverse.banners;

public enum BannerLimits {

    MAX_WIDTH(728),
    MAX_HEIGHT(90),
    MAX_SIZE_IN_BYTES(1000000);

    BannerLimits(int value) {
        this.value = value;
    }

    private int value;

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }
}
