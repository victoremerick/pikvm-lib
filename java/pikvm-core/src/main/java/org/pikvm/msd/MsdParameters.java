package org.pikvm.msd;

/**
 * Parameters for configuring an MSD image.
 * Equivalent to the arguments of {@code set_msd_parameters()} in Python.
 */
public final class MsdParameters {
    private final String imageName;
    /** true = cdrom mode, false = flash mode */
    private final boolean cdrom;

    private MsdParameters(Builder b) {
        this.imageName = b.imageName;
        this.cdrom     = b.cdrom;
    }

    public String  getImageName() { return imageName; }
    public boolean isCdrom()      { return cdrom; }
    /** Returns 1 for cdrom, 0 for flash – matches PiKVM API expectation. */
    public int     getCdromFlag() { return cdrom ? 1 : 0; }

    public static Builder builder(String imageName) { return new Builder(imageName); }

    public static final class Builder {
        private final String imageName;
        private boolean cdrom = false;

        private Builder(String imageName) { this.imageName = imageName; }

        /** Use cdrom mode (default: flash mode). */
        public Builder cdrom(boolean cdrom) { this.cdrom = cdrom; return this; }

        public MsdParameters build() { return new MsdParameters(this); }
    }
}
