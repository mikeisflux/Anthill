# StreamLick Rollback Procedures

This document outlines the rollback procedures for reverting from Ant Media Server to the previous mediasoup implementation if issues arise.

## Domain Configuration
- **Media Server**: `media.streamlick.com` (Ant Media - systemd)
- **Backend API**: `api.streamlick.com` (Node.js - pm2)

---

## Rollback Scenarios

### Scenario 1: Quick Rollback (Feature Flag)
**Use when**: Minor issues, testing failures, or gradual rollout reversal

**Time to complete**: < 5 minutes

### Scenario 2: Full Service Rollback
**Use when**: Critical failures, data corruption, or complete system failure

**Time to complete**: 15-30 minutes

### Scenario 3: Emergency Rollback
**Use when**: Production is down, immediate action required

**Time to complete**: < 2 minutes

---

## Scenario 1: Quick Rollback (Feature Flag)

### Prerequisites
- Feature flag system configured in frontend
- Both Ant Media and mediasoup services installed

### Steps

1. **Update Environment Variable**
   ```bash
   # On frontend server or in .env file
   VITE_USE_ANT_MEDIA=false
   ```

2. **Rebuild Frontend (if needed)**
   ```bash
   cd /opt/streamlick-frontend
   npm run build
   ```

3. **Deploy Updated Frontend**
   ```bash
   # Using your deployment method
   npm run deploy
   # or
   rsync -avz dist/ user@server:/var/www/streamlick/
   ```

4. **Verify Rollback**
   - Open https://streamlick.com
   - Check browser console for mediasoup connections
   - Test a broadcast to confirm mediasoup is active

5. **Monitor**
   - Watch error logs for any residual issues
   - Verify RTMP destinations still work (via mediasoup)

### Reverting Back to Ant Media
```bash
VITE_USE_ANT_MEDIA=true
npm run build && npm run deploy
```

---

## Scenario 2: Full Service Rollback

### Prerequisites
- SSH access to server
- mediasoup installation present at /opt/mediasoup
- Backup of any persistent data

### Steps

1. **Stop Ant Media Server**
   ```bash
   # Stop Ant Media systemd service
   sudo systemctl stop antmedia

   # Verify it's stopped
   sudo systemctl status antmedia

   # Disable auto-start (optional)
   sudo systemctl disable antmedia
   ```

2. **Start Mediasoup Service**
   ```bash
   # If mediasoup is managed by pm2
   pm2 start /opt/mediasoup/server.js --name mediasoup

   # Or if using systemd for mediasoup
   sudo systemctl start mediasoup

   # Verify mediasoup is running
   curl http://localhost:3001/health
   ```

3. **Update Nginx Configuration (if using reverse proxy)**
   ```bash
   # Edit nginx config
   sudo nano /etc/nginx/conf.d/media.streamlick.com.conf

   # Change upstream from antmedia to mediasoup
   # Before:
   #   proxy_pass http://localhost:5080;
   # After:
   #   proxy_pass http://localhost:3001;

   # Test and reload nginx
   sudo nginx -t && sudo systemctl reload nginx
   ```

4. **Update Backend API Configuration**
   ```bash
   # Update api.streamlick.com to point to mediasoup
   cd /opt/streamlick-api

   # Edit .env file
   nano .env
   # Change:
   #   MEDIA_SERVER_TYPE=mediasoup
   #   MEDIA_SERVER_URL=http://localhost:3001

   # Restart backend
   pm2 restart streamlick-api
   ```

5. **Update Frontend Environment**
   ```bash
   cd /opt/streamlick-frontend

   # Update .env
   echo "VITE_USE_ANT_MEDIA=false" >> .env

   # Rebuild and deploy
   npm run build && npm run deploy
   ```

6. **Verify Full Rollback**
   ```bash
   # Check mediasoup is running
   curl http://localhost:3001/health

   # Check API is connecting to mediasoup
   curl https://api.streamlick.com/health

   # Test broadcast functionality manually
   ```

---

## Scenario 3: Emergency Rollback

### For Immediate Production Recovery

Create this script at `/opt/scripts/emergency-rollback.sh`:

```bash
#!/bin/bash
# emergency-rollback.sh
# Run this script for immediate rollback from Ant Media to mediasoup

set -e

echo "=== EMERGENCY ROLLBACK INITIATED ==="
echo "Time: $(date)"

# 1. Stop Ant Media immediately
echo "Stopping Ant Media..."
sudo systemctl stop antmedia || true

# 2. Start mediasoup
echo "Starting mediasoup..."
pm2 start /opt/mediasoup/server.js --name mediasoup 2>/dev/null || pm2 restart mediasoup

# 3. Wait for mediasoup to be healthy
echo "Waiting for mediasoup health check..."
for i in {1..30}; do
    if curl -s http://localhost:3001/health > /dev/null 2>&1; then
        echo "mediasoup is healthy!"
        break
    fi
    echo "Waiting... ($i/30)"
    sleep 1
done

# 4. Update nginx (if backup config exists)
if [ -f /etc/nginx/conf.d/media.streamlick.com.conf.mediasoup ]; then
    echo "Switching nginx to mediasoup config..."
    sudo cp /etc/nginx/conf.d/media.streamlick.com.conf.mediasoup \
            /etc/nginx/conf.d/media.streamlick.com.conf
    sudo nginx -t && sudo systemctl reload nginx
fi

# 5. Restart backend to pick up mediasoup connection
echo "Restarting backend API..."
pm2 restart streamlick-api || true

echo "=== EMERGENCY ROLLBACK COMPLETE ==="
echo "Please verify system functionality manually:"
echo "  - curl http://localhost:3001/health"
echo "  - curl https://media.streamlick.com/health"
echo ""
echo "IMPORTANT: Frontend still needs to be rebuilt with VITE_USE_ANT_MEDIA=false"
```

### Usage
```bash
chmod +x /opt/scripts/emergency-rollback.sh
sudo /opt/scripts/emergency-rollback.sh
```

---

## Post-Rollback Checklist

After any rollback, complete the following verification steps:

### Immediate (< 5 minutes)
- [ ] Media server responding (health check)
- [ ] Frontend loads without errors
- [ ] WebRTC connections establishing

### Short-term (< 30 minutes)
- [ ] Test broadcast creation
- [ ] Test RTMP destination addition
- [ ] Test stream start/stop
- [ ] Verify webhook notifications to api.streamlick.com

### Extended (< 2 hours)
- [ ] Run full test broadcast
- [ ] Verify multi-destination streaming
- [ ] Check resource usage is normal
- [ ] Review error logs

---

## Rollback Decision Matrix

| Issue | Severity | Recommended Action |
|-------|----------|-------------------|
| Latency > 2s | Low | Monitor, consider Scenario 1 |
| Single endpoint failing | Low | Investigate, no rollback |
| All endpoints failing | High | Scenario 1 immediately |
| WebRTC not connecting | Critical | Scenario 3 emergency |
| Server crash/unresponsive | Critical | Scenario 3 emergency |
| Data corruption | Critical | Scenario 2 + data recovery |
| Memory leak (gradual) | Medium | Scenario 1, investigate |
| API integration broken | High | Scenario 2 |

---

## Data Recovery

### Ant Media Data Locations
```
/usr/local/antmedia/webapps/StreamLick/streams/    # Live stream data
/usr/local/antmedia/webapps/StreamLick/recordings/ # Recorded streams
/usr/local/antmedia/webapps/StreamLick/streamlick.db # MapDB database
```

### Backup Before Rollback
```bash
# Create backup of Ant Media data
sudo tar -czvf /opt/backups/antmedia-backup-$(date +%Y%m%d-%H%M%S).tar.gz \
    /usr/local/antmedia/webapps/StreamLick/
```

### Restore After Rollback (if needed)
```bash
# Stop Ant Media first
sudo systemctl stop antmedia

# Restore Ant Media data
sudo tar -xzvf /opt/backups/antmedia-backup-*.tar.gz -C /

# Fix permissions
sudo chown -R antmedia:antmedia /usr/local/antmedia/webapps/StreamLick/

# Restart Ant Media
sudo systemctl start antmedia
```

---

## Service Management Quick Reference

### Ant Media Server (systemd)
```bash
# Start/Stop/Restart
sudo systemctl start antmedia
sudo systemctl stop antmedia
sudo systemctl restart antmedia

# Check status
sudo systemctl status antmedia

# View logs
sudo journalctl -u antmedia -f
tail -f /usr/local/antmedia/log/ant-media-server.log
```

### Mediasoup (pm2)
```bash
# Start/Stop/Restart
pm2 start mediasoup
pm2 stop mediasoup
pm2 restart mediasoup

# Check status
pm2 status

# View logs
pm2 logs mediasoup
```

### Backend API (pm2)
```bash
pm2 restart streamlick-api
pm2 logs streamlick-api
```

---

## Contact Information

### Escalation Path
1. **L1 Support**: Check monitoring dashboards, attempt Scenario 1
2. **L2 DevOps**: Execute Scenario 2 if Scenario 1 fails
3. **L3 Engineering**: Emergency Scenario 3, root cause analysis

### Key Contacts
- DevOps On-Call: [phone/slack]
- Backend Lead: [phone/slack]
- Infrastructure: [phone/slack]

---

## Pre-Deployment Preparation

Before going live with Ant Media, ensure rollback capability:

1. **Create nginx config backups**
   ```bash
   sudo cp /etc/nginx/conf.d/media.streamlick.com.conf \
           /etc/nginx/conf.d/media.streamlick.com.conf.antmedia

   # Create mediasoup version
   sudo cp /etc/nginx/conf.d/media.streamlick.com.conf.original \
           /etc/nginx/conf.d/media.streamlick.com.conf.mediasoup
   ```

2. **Keep mediasoup installed but stopped**
   ```bash
   pm2 stop mediasoup
   pm2 save
   ```

3. **Create the emergency rollback script**
   ```bash
   sudo mkdir -p /opt/scripts
   # Copy the emergency-rollback.sh script above
   sudo chmod +x /opt/scripts/emergency-rollback.sh
   ```

4. **Test rollback in staging first**
   - Simulate failure
   - Execute rollback
   - Verify recovery

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2024-XX-XX | StreamLick Team | Initial rollback procedures |
| 1.1 | 2024-XX-XX | StreamLick Team | Updated for systemd/pm2 (removed Docker) |
