package org.pikvm.webrtc;

/**
 * Represents a WebRTC ICE (Interactive Connectivity Establishment) candidate.
 *
 * <p>ICE candidates describe possible network paths between the local client
 * and the remote PiKVM device.  They are delivered by PiKVM via WebSocket
 * events with {@code event_type = "streamer_webrtc"} and forwarded to the
 * WebRTC peer connection so that connectivity can be established.</p>
 */
public final class WebRtcIceCandidate {

    private final String candidate;
    private final String sdpMid;
    private final int    sdpMLineIndex;

    /**
     * Creates a new ICE candidate.
     *
     * @param candidate    the SDP candidate string (e.g. {@code "candidate:…"})
     * @param sdpMid       the media stream identification tag
     * @param sdpMLineIndex the zero-based index of the media description in the SDP
     */
    public WebRtcIceCandidate(String candidate, String sdpMid, int sdpMLineIndex) {
        this.candidate     = candidate;
        this.sdpMid        = sdpMid;
        this.sdpMLineIndex = sdpMLineIndex;
    }

    /** Returns the SDP candidate attribute string. */
    public String getCandidate()     { return candidate; }

    /** Returns the media stream identification tag. */
    public String getSdpMid()        { return sdpMid; }

    /** Returns the zero-based index of the media description in the SDP. */
    public int    getSdpMLineIndex() { return sdpMLineIndex; }

    @Override
    public String toString() {
        return "WebRtcIceCandidate{sdpMid='" + sdpMid
                + "', sdpMLineIndex=" + sdpMLineIndex
                + ", candidate='" + candidate + "'}";
    }
}
