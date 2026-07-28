package org.pikvm.msd;

import org.pikvm.endpoint.BaseEndpoint;
import org.pikvm.exception.PiKvmApiException;
import org.pikvm.exception.PiKvmNetworkException;
import org.pikvm.http.PiKvmHttpClient;
import okhttp3.Response;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Mass Storage Device (MSD) client.
 * <p>
 * Equivalent to {@code PiKVMMSD} in Python.
 * </p>
 */
public class MsdClient extends BaseEndpoint {

    private static final Logger LOGGER           = Logger.getLogger(MsdClient.class.getName());
    private static final String BASE_PATH        = "/api/msd";
    private static final String WRITE_PATH       = "/api/msd/write";
    private static final String WRITE_REMOTE_PATH = "/api/msd/write_remote";
    private static final String SET_PARAMS_PATH  = "/api/msd/set_params";
    private static final String SET_CONN_PATH    = "/api/msd/set_connected";
    private static final String REMOVE_PATH      = "/api/msd/remove";
    private static final String RESET_PATH       = "/api/msd/reset";

    public MsdClient(PiKvmHttpClient httpClient) {
        super(httpClient);
    }

    /**
     * Returns current MSD subsystem state.
     * Equivalent to {@code get_msd_state()}.
     */
    public Map<String, Object> getMsdState() {
        return getEndpointState(BASE_PATH);
    }

    /**
     * Uploads a local file as an MSD image.
     * Equivalent to {@code upload_msd_image(filepath, image_name)}.
     *
     * @param file      local file to upload
     * @param imageName name for the image on PiKVM (defaults to filename if null)
     */
    public void uploadMsdImage(File file, String imageName) {
        String name = (imageName != null) ? imageName : file.getName();
        try (FileInputStream fis = new FileInputStream(file);
             Response resp = httpClient.postStream(WRITE_PATH, "image=" + name,
                                                    fis, file.length())) {
            if (!resp.isSuccessful()) {
                throw new PiKvmApiException("MSD upload failed: " + resp.code(), resp.code());
            }
            LOGGER.warning("Image " + name + " uploaded");
        } catch (IOException e) {
            throw new PiKvmNetworkException("Failed to upload MSD image", e);
        }
    }

    /** Overload with auto image name. */
    public void uploadMsdImage(File file) {
        uploadMsdImage(file, null);
    }

    /**
     * Uploads an MSD image from a remote URL.
     * Equivalent to {@code upload_msd_remote(remote, image_name)}.
     */
    public void uploadMsdRemote(String url, String imageName) {
        String opts = "url=" + url + (imageName != null ? "&image=" + imageName : "");
        try (Response resp = httpClient.post(WRITE_REMOTE_PATH, opts)) {
            if (!resp.isSuccessful()) {
                throw new PiKvmApiException("MSD remote upload failed: " + resp.code(), resp.code());
            }
        } catch (IOException e) {
            throw new PiKvmNetworkException("Failed to upload remote MSD image", e);
        }
    }

    /** Overload with auto image name. */
    public void uploadMsdRemote(String url) {
        uploadMsdRemote(url, null);
    }

    /**
     * Sets MSD parameters.
     * Equivalent to {@code set_msd_parameters(image_name, cdrom, flash)}.
     */
    public void setMsdParameters(MsdParameters params) {
        String opts = "image=" + params.getImageName() + "&cdrom=" + params.getCdromFlag();
        try (Response resp = httpClient.post(SET_PARAMS_PATH, opts)) {
            if (!resp.isSuccessful()) {
                throw new PiKvmApiException("Set MSD params failed: " + resp.code(), resp.code());
            }
        } catch (IOException e) {
            throw new PiKvmNetworkException("Failed to set MSD parameters", e);
        }
    }

    /** Connects the MSD. Equivalent to {@code connect_msd()}. */
    public void connectMsd() {
        controlMsd(true);
    }

    /** Disconnects the MSD. Equivalent to {@code disconnect_msd()}. */
    public void disconnectMsd() {
        controlMsd(false);
    }

    /**
     * Removes an MSD image by name.
     * Equivalent to {@code remove_msd_image(image_name)}.
     */
    public void removeMsdImage(String imageName) {
        try (Response resp = httpClient.post(REMOVE_PATH, "image=" + imageName)) {
            if (!resp.isSuccessful()) {
                throw new PiKvmApiException("Remove MSD image failed: " + resp.code(), resp.code());
            }
        } catch (IOException e) {
            throw new PiKvmNetworkException("Failed to remove MSD image", e);
        }
    }

    /** Resets the MSD subsystem. Equivalent to {@code reset_msd()}. */
    public void resetMsd() {
        try (Response resp = httpClient.post(RESET_PATH, null)) {
            if (!resp.isSuccessful()) {
                throw new PiKvmApiException("Reset MSD failed: " + resp.code(), resp.code());
            }
        } catch (IOException e) {
            throw new PiKvmNetworkException("Failed to reset MSD", e);
        }
    }

    private void controlMsd(boolean connected) {
        httpClient.postsInt(SET_CONN_PATH, "connected", connected ? 1 : 0, Set.of(0, 1));
        LOGGER.warning("MSD " + (connected ? "connected" : "disconnected") + "!");
    }
}
