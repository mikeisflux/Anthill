package io.antmedia.muxer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.datastore.db.types.Endpoint;
import io.antmedia.rest.model.Result;
import io.vertx.core.Vertx;

/**
 * MultiRtmpManager - Manages multiple RTMP destinations for a single stream.
 *
 * This class provides a fallback implementation for multi-destination RTMP streaming
 * when the enterprise features are not available. It tracks multiple RTMP endpoints
 * for a single broadcast and coordinates the streaming to all destinations.
 *
 * Key features:
 * - Concurrent management of multiple RTMP endpoints
 * - Thread-safe endpoint addition and removal
 * - Status tracking for each endpoint
 * - Health check coordination
 * - Graceful shutdown of all endpoints
 *
 * @author StreamLick Integration
 * @since 3.0.0
 */
public class MultiRtmpManager {

    private static final Logger logger = LoggerFactory.getLogger(MultiRtmpManager.class);

    /**
     * Maps stream IDs to their associated RTMP endpoint URLs
     */
    private final ConcurrentHashMap<String, Set<String>> streamEndpoints;

    /**
     * Maps endpoint URLs to their RtmpMuxer instances
     */
    private final ConcurrentHashMap<String, RtmpMuxer> endpointMuxers;

    /**
     * Maps endpoint URLs to their current status
     */
    private final ConcurrentHashMap<String, String> endpointStatus;

    /**
     * Indicates whether the manager is running
     */
    private final AtomicBoolean isRunning;

    /**
     * Vertx instance for async operations
     */
    private Vertx vertx;

    /**
     * Maximum number of RTMP endpoints allowed per stream
     */
    private int maxEndpointsPerStream = 10;

    /**
     * Retry limit for failed endpoints
     */
    private int retryLimit = 3;

    /**
     * Health check period in milliseconds
     */
    private int healthCheckPeriodMs = 2000;

    public MultiRtmpManager() {
        this.streamEndpoints = new ConcurrentHashMap<>();
        this.endpointMuxers = new ConcurrentHashMap<>();
        this.endpointStatus = new ConcurrentHashMap<>();
        this.isRunning = new AtomicBoolean(true);
    }

    public MultiRtmpManager(Vertx vertx) {
        this();
        this.vertx = vertx;
    }

    /**
     * Adds an RTMP endpoint for a given stream.
     *
     * @param streamId The ID of the stream
     * @param endpoint The endpoint to add
     * @return Result indicating success or failure
     */
    public Result addEndpoint(String streamId, Endpoint endpoint) {
        Result result = new Result(false);

        if (streamId == null || endpoint == null || endpoint.getRtmpUrl() == null) {
            result.setMessage("Invalid stream ID or endpoint");
            return result;
        }

        String rtmpUrl = endpoint.getRtmpUrl();

        // Get or create the set of endpoints for this stream
        Set<String> endpoints = streamEndpoints.computeIfAbsent(streamId,
            k -> ConcurrentHashMap.newKeySet());

        // Check if we've reached the maximum number of endpoints
        if (endpoints.size() >= maxEndpointsPerStream) {
            result.setMessage("Maximum number of endpoints (" + maxEndpointsPerStream + ") reached for stream: " + streamId);
            logger.warn("Maximum endpoints reached for stream: {}", streamId);
            return result;
        }

        // Check if endpoint already exists
        if (endpoints.contains(rtmpUrl)) {
            result.setMessage("Endpoint already exists for stream: " + streamId);
            logger.info("Endpoint {} already exists for stream {}", rtmpUrl, streamId);
            return result;
        }

        // Add the endpoint
        endpoints.add(rtmpUrl);
        endpointStatus.put(rtmpUrl, "pending");

        logger.info("Added RTMP endpoint {} for stream {}", sanitizeUrl(rtmpUrl), streamId);
        result.setSuccess(true);
        result.setMessage("Endpoint added successfully");

        return result;
    }

    /**
     * Removes an RTMP endpoint from a stream.
     *
     * @param streamId The ID of the stream
     * @param rtmpUrl The RTMP URL to remove
     * @return Result indicating success or failure
     */
    public Result removeEndpoint(String streamId, String rtmpUrl) {
        Result result = new Result(false);

        if (streamId == null || rtmpUrl == null) {
            result.setMessage("Invalid stream ID or RTMP URL");
            return result;
        }

        Set<String> endpoints = streamEndpoints.get(streamId);
        if (endpoints == null || !endpoints.contains(rtmpUrl)) {
            result.setMessage("Endpoint not found for stream: " + streamId);
            return result;
        }

        // Stop the muxer if it's running
        RtmpMuxer muxer = endpointMuxers.remove(rtmpUrl);
        if (muxer != null) {
            try {
                muxer.writeTrailer();
            } catch (Exception e) {
                logger.error("Error stopping RTMP muxer for {}: {}", sanitizeUrl(rtmpUrl), e.getMessage());
            }
        }

        // Remove from tracking
        endpoints.remove(rtmpUrl);
        endpointStatus.remove(rtmpUrl);

        logger.info("Removed RTMP endpoint {} from stream {}", sanitizeUrl(rtmpUrl), streamId);
        result.setSuccess(true);
        result.setMessage("Endpoint removed successfully");

        return result;
    }

    /**
     * Gets all endpoints for a stream.
     *
     * @param streamId The ID of the stream
     * @return Set of RTMP URLs for the stream
     */
    public Set<String> getEndpoints(String streamId) {
        return streamEndpoints.getOrDefault(streamId, ConcurrentHashMap.newKeySet());
    }

    /**
     * Gets the status of a specific endpoint.
     *
     * @param rtmpUrl The RTMP URL
     * @return The status string
     */
    public String getEndpointStatus(String rtmpUrl) {
        return endpointStatus.getOrDefault(rtmpUrl, "unknown");
    }

    /**
     * Updates the status of an endpoint.
     *
     * @param rtmpUrl The RTMP URL
     * @param status The new status
     */
    public void updateEndpointStatus(String rtmpUrl, String status) {
        if (rtmpUrl != null && status != null) {
            endpointStatus.put(rtmpUrl, status);
            logger.debug("Updated endpoint {} status to {}", sanitizeUrl(rtmpUrl), status);
        }
    }

    /**
     * Registers an RtmpMuxer for an endpoint.
     *
     * @param rtmpUrl The RTMP URL
     * @param muxer The RtmpMuxer instance
     */
    public void registerMuxer(String rtmpUrl, RtmpMuxer muxer) {
        if (rtmpUrl != null && muxer != null) {
            endpointMuxers.put(rtmpUrl, muxer);
            endpointStatus.put(rtmpUrl, "broadcasting");
        }
    }

    /**
     * Gets the muxer for a specific endpoint.
     *
     * @param rtmpUrl The RTMP URL
     * @return The RtmpMuxer instance or null
     */
    public RtmpMuxer getMuxer(String rtmpUrl) {
        return endpointMuxers.get(rtmpUrl);
    }

    /**
     * Stops all endpoints for a stream.
     *
     * @param streamId The ID of the stream
     */
    public void stopAllEndpoints(String streamId) {
        Set<String> endpoints = streamEndpoints.remove(streamId);
        if (endpoints != null) {
            for (String rtmpUrl : endpoints) {
                RtmpMuxer muxer = endpointMuxers.remove(rtmpUrl);
                if (muxer != null) {
                    try {
                        muxer.writeTrailer();
                        logger.info("Stopped RTMP muxer for endpoint {}", sanitizeUrl(rtmpUrl));
                    } catch (Exception e) {
                        logger.error("Error stopping RTMP muxer for {}: {}", sanitizeUrl(rtmpUrl), e.getMessage());
                    }
                }
                endpointStatus.remove(rtmpUrl);
            }
        }
    }

    /**
     * Gets the total number of active endpoints across all streams.
     *
     * @return Total endpoint count
     */
    public int getTotalEndpointCount() {
        return endpointMuxers.size();
    }

    /**
     * Gets the number of endpoints for a specific stream.
     *
     * @param streamId The ID of the stream
     * @return Number of endpoints for the stream
     */
    public int getEndpointCount(String streamId) {
        Set<String> endpoints = streamEndpoints.get(streamId);
        return endpoints != null ? endpoints.size() : 0;
    }

    /**
     * Gets a map of all endpoint statuses for a stream.
     *
     * @param streamId The ID of the stream
     * @return Map of RTMP URL to status
     */
    public Map<String, String> getEndpointStatuses(String streamId) {
        Map<String, String> statuses = new ConcurrentHashMap<>();
        Set<String> endpoints = streamEndpoints.get(streamId);
        if (endpoints != null) {
            for (String rtmpUrl : endpoints) {
                statuses.put(rtmpUrl, endpointStatus.getOrDefault(rtmpUrl, "unknown"));
            }
        }
        return statuses;
    }

    /**
     * Checks if a stream has any endpoints.
     *
     * @param streamId The ID of the stream
     * @return true if the stream has endpoints
     */
    public boolean hasEndpoints(String streamId) {
        Set<String> endpoints = streamEndpoints.get(streamId);
        return endpoints != null && !endpoints.isEmpty();
    }

    /**
     * Shuts down the manager and stops all endpoints.
     */
    public void shutdown() {
        isRunning.set(false);
        logger.info("Shutting down MultiRtmpManager");

        // Stop all muxers
        for (Map.Entry<String, RtmpMuxer> entry : endpointMuxers.entrySet()) {
            try {
                entry.getValue().writeTrailer();
            } catch (Exception e) {
                logger.error("Error stopping muxer during shutdown: {}", e.getMessage());
            }
        }

        // Clear all maps
        streamEndpoints.clear();
        endpointMuxers.clear();
        endpointStatus.clear();

        logger.info("MultiRtmpManager shutdown complete");
    }

    /**
     * Sanitizes an RTMP URL for logging by masking sensitive parts.
     *
     * @param url The URL to sanitize
     * @return Sanitized URL
     */
    private String sanitizeUrl(String url) {
        if (url == null) {
            return "null";
        }
        // Mask stream keys and other sensitive information
        return url.replaceAll("[\n\r\t]", "_")
                  .replaceAll("([?&]key=)[^&]*", "$1***")
                  .replaceAll("([?&]token=)[^&]*", "$1***");
    }

    // Getters and Setters

    public int getMaxEndpointsPerStream() {
        return maxEndpointsPerStream;
    }

    public void setMaxEndpointsPerStream(int maxEndpointsPerStream) {
        this.maxEndpointsPerStream = maxEndpointsPerStream;
    }

    public int getRetryLimit() {
        return retryLimit;
    }

    public void setRetryLimit(int retryLimit) {
        this.retryLimit = retryLimit;
    }

    public int getHealthCheckPeriodMs() {
        return healthCheckPeriodMs;
    }

    public void setHealthCheckPeriodMs(int healthCheckPeriodMs) {
        this.healthCheckPeriodMs = healthCheckPeriodMs;
    }

    public Vertx getVertx() {
        return vertx;
    }

    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}
