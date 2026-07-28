package org.pikvm.desktop;

import org.pikvm.streamer.SnapshotResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Desktop-specific helpers for decoding PiKVM streamer snapshots.
 * <p>
 * Decodes {@link SnapshotResult} raw JPEG bytes into {@link BufferedImage}
 * using the standard Java {@code javax.imageio} API (available on Java 17+).
 * </p>
 *
 * <p>Usage:
 * <pre>{@code
 * SnapshotResult snap = pikvm.getStreamerImage();
 * BufferedImage  img  = StreamerDesktop.decodeImage(snap);
 * int width  = img.getWidth();
 * int height = img.getHeight();
 * }</pre>
 * </p>
 */
public final class StreamerDesktop {

    private StreamerDesktop() {}

    /**
     * Decodes raw snapshot bytes into a {@link BufferedImage}.
     *
     * @param result snapshot from {@code PiKvmClient.getStreamerImage()}
     * @return decoded image
     * @throws IOException if the bytes do not represent a valid JPEG
     */
    public static BufferedImage decodeImage(SnapshotResult result) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(result.getImageBytes()));
    }

    /**
     * Decodes raw JPEG bytes into a {@link BufferedImage}.
     *
     * @param jpegBytes raw JPEG bytes
     * @return decoded image
     * @throws IOException if the bytes do not represent a valid JPEG
     */
    public static BufferedImage decodeImage(byte[] jpegBytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpegBytes));
    }
}
