# StreamLick Testing Checklist

This document provides a comprehensive testing checklist for validating the Ant Media Server integration with StreamLick.

## Domain Configuration
- **Media Server**: `media.streamlick.com`
- **Backend API**: `api.streamlick.com`

---

## 1. Functional Testing

### 1.1 WebRTC Connection
- [ ] WebRTC connection establishes successfully from browser
- [ ] ICE candidates are gathered properly
- [ ] DTLS handshake completes without timeout
- [ ] Connection works with STUN server (stun.l.google.com:19302)
- [ ] Connection works with TURN server (if configured)
- [ ] Connection recovers from temporary network drops

### 1.2 Canvas Stream Publishing
- [ ] Canvas MediaStream captures correctly
- [ ] Video track published successfully
- [ ] Audio track published successfully
- [ ] Stream appears in Ant Media dashboard
- [ ] Stream ID is correctly assigned

### 1.3 Single RTMP Destination
- [ ] Add single RTMP endpoint via REST API
- [ ] Stream starts on destination (YouTube/Facebook/Twitch)
- [ ] Video quality matches source
- [ ] Audio synchronization is correct
- [ ] Remove endpoint stops streaming

### 1.4 Multiple RTMP Destinations
- [ ] Add multiple endpoints via batch API (`/rtmp-endpoints-batch`)
- [ ] All destinations receive stream simultaneously
- [ ] Individual endpoint status is trackable
- [ ] Remove single endpoint doesn't affect others
- [ ] All endpoints stop on broadcast end

### 1.5 Stream Lifecycle
- [ ] Broadcast creates successfully via API
- [ ] Publish token generation works
- [ ] Stream starts broadcasting
- [ ] Stream statistics are collected
- [ ] Stream ends cleanly without errors
- [ ] Webhook notifications sent to api.streamlick.com

### 1.6 Authentication & Security
- [ ] Publish token validates correctly
- [ ] Invalid tokens are rejected
- [ ] CORS headers allow frontend domain
- [ ] SSL/TLS certificate is valid for media.streamlick.com

---

## 2. Performance Testing

### 2.1 Latency Metrics
| Metric | Target | Actual | Pass/Fail |
|--------|--------|--------|-----------|
| WebRTC glass-to-glass latency | < 500ms | | |
| RTMP output latency | < 2s | | |
| API response time | < 200ms | | |
| Endpoint addition time | < 1s | | |

### 2.2 Resource Usage
| Metric | Limit | Actual | Pass/Fail |
|--------|-------|--------|-----------|
| CPU usage (idle) | < 5% | | |
| CPU usage (1 stream) | < 30% | | |
| CPU usage (5 streams) | < 60% | | |
| Memory usage (idle) | < 2GB | | |
| Memory usage (5 streams) | < 6GB | | |

### 2.3 Concurrent Streams
- [ ] 2 concurrent broadcasts stable
- [ ] 5 concurrent broadcasts stable
- [ ] 10 concurrent broadcasts stable
- [ ] No memory leaks over 1 hour
- [ ] No CPU spikes during peak load

### 2.4 Bandwidth
- [ ] Single 1080p stream: ~4-6 Mbps
- [ ] Multiple destinations don't multiply bandwidth
- [ ] Network buffer handling is smooth

---

## 3. Integration Testing

### 3.1 YouTube Live
- [ ] RTMP endpoint accepts YouTube stream key
- [ ] Stream appears in YouTube Studio
- [ ] Stream quality: 1080p @ 30fps
- [ ] Audio bitrate: 128kbps
- [ ] Stream remains stable for 30+ minutes

### 3.2 Facebook Live
- [ ] RTMP endpoint accepts Facebook stream key
- [ ] Stream appears in Facebook Creator Studio
- [ ] Video/audio quality is acceptable
- [ ] Stream remains stable

### 3.3 Twitch
- [ ] RTMP endpoint accepts Twitch stream key
- [ ] Stream appears on Twitch channel
- [ ] Low latency mode working
- [ ] Chat integration (if applicable)

### 3.4 Custom RTMP Destinations
- [ ] Custom RTMP URL works (e.g., nginx-rtmp)
- [ ] Authentication parameters passed correctly
- [ ] Stream key URL encoding handled

### 3.5 Backend Integration (api.streamlick.com)
- [ ] Webhook received on stream start
- [ ] Webhook received on stream end
- [ ] Webhook received on endpoint error
- [ ] VOD finished webhook works
- [ ] JWT token validation with backend

---

## 4. Error Handling Testing

### 4.1 Network Failures
- [ ] Stream recovers from brief network interruption
- [ ] Endpoint retry logic works (3 attempts)
- [ ] Error webhook sent after retry exhaustion
- [ ] User notified of failure

### 4.2 Invalid Input Handling
- [ ] Invalid stream ID returns proper error
- [ ] Invalid RTMP URL returns proper error
- [ ] Duplicate endpoint addition handled
- [ ] Maximum endpoints limit enforced

### 4.3 Resource Exhaustion
- [ ] Memory exhaustion handled gracefully
- [ ] CPU throttling doesn't crash server
- [ ] Disk space warnings generated

---

## 5. Rollback Testing

### 5.1 Quick Rollback (Feature Flag)
- [ ] Set `VITE_USE_ANT_MEDIA=false` in frontend
- [ ] Frontend reverts to mediasoup
- [ ] No data loss during rollback
- [ ] Active streams handled gracefully

### 5.2 Full Rollback
- [ ] Stop Ant Media container
- [ ] Start mediasoup container
- [ ] Verify mediasoup functionality
- [ ] Database state preserved

---

## 6. API Endpoint Testing

### 6.1 Broadcast Endpoints
```bash
# Create broadcast
curl -X POST https://media.streamlick.com/StreamLick/rest/v2/broadcasts \
  -H "Content-Type: application/json" \
  -d '{"name": "test-stream"}'

# Get broadcast info
curl https://media.streamlick.com/StreamLick/rest/v2/broadcasts/{streamId}

# Delete broadcast
curl -X DELETE https://media.streamlick.com/StreamLick/rest/v2/broadcasts/{streamId}
```

### 6.2 RTMP Endpoint Management
```bash
# Add single RTMP endpoint
curl -X POST https://media.streamlick.com/StreamLick/rest/v2/broadcasts/{streamId}/rtmp-endpoint \
  -H "Content-Type: application/json" \
  -d '{"rtmpUrl": "rtmp://a.rtmp.youtube.com/live2/YOUR_STREAM_KEY"}'

# Add multiple RTMP endpoints (batch)
curl -X POST https://media.streamlick.com/StreamLick/rest/v2/broadcasts/{streamId}/rtmp-endpoints-batch \
  -H "Content-Type: application/json" \
  -d '[
    {"rtmpUrl": "rtmp://a.rtmp.youtube.com/live2/KEY1"},
    {"rtmpUrl": "rtmp://live-api-s.facebook.com:443/rtmp/KEY2"}
  ]'

# Remove RTMP endpoint
curl -X DELETE "https://media.streamlick.com/StreamLick/rest/v2/broadcasts/{streamId}/rtmp-endpoint?endpointServiceId={id}"
```

---

## 7. Sign-Off

| Test Category | Tester | Date | Result |
|---------------|--------|------|--------|
| Functional Testing | | | |
| Performance Testing | | | |
| Integration Testing | | | |
| Error Handling | | | |
| Rollback Testing | | | |
| API Testing | | | |

**Final Approval**: _________________ Date: _________

---

## Notes

_Add any testing notes, observations, or issues discovered during testing:_

1.
2.
3.
