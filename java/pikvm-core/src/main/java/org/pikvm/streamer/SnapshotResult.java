package org.pikvm.streamer;

/**
 * Result of a PiKVM streamer snapshot request.
 * <p>
 * The core library always returns raw bytes; platform-specific adapters
 * (pikvm-android, pikvm-desktop) decode these into native image types.
 * </p>
 */
public final class SnapshotResult {
    /** Raw JPEG bytes of the snapshot image. */
    private final byte[] imageBytes;
    /** OCR text content (only set when OCR mode was requested; null otherwise). */
    private final String ocrText;
    /** True if this result contains OCR text instead of image bytes. */
    private final boolean isOcr;

    private SnapshotResult(byte[] imageBytes, String ocrText, boolean isOcr) {
        this.imageBytes = imageBytes;
        this.ocrText    = ocrText;
        this.isOcr      = isOcr;
    }

    public static SnapshotResult ofImage(byte[] imageBytes) {
        return new SnapshotResult(imageBytes, null, false);
    }

    public static SnapshotResult ofOcr(byte[] textBytes) {
        return new SnapshotResult(textBytes, new String(textBytes, java.nio.charset.StandardCharsets.UTF_8), true);
    }

    public byte[] getImageBytes() { return imageBytes; }
    public String getOcrText()    { return ocrText; }
    public boolean isOcr()        { return isOcr; }
}
