# EC2 Deployment Quick Reference

## 📋 Pre-Deployment Checklist

```bash
# 1. Build JAR files locally
cd upload-service-main && ./gradlew clean build -x test
cd ../validation_service && ./gradlew clean build -x test

# 2. Verify JAR files exist
ls upload-service-main/build/libs/*.jar
ls validation_service/build/libs/*.jar
```

---

## 🚀 Fast Deployment (5 Steps)

### 1. Launch EC2 Instance
```bash
# AWS Console: EC2 → Launch Instance
# - AMI: Ubuntu 22.04 LTS
# - Type: t3.medium (2 vCPU, 4GB RAM)
# - Security Group: Allow SSH(22), HTTP(80), Port 8080, 8081
# - IAM Role: Attach role with S3, SQS, SNS, SES permissions
```

### 2. Connect to EC2
```bash
ssh -i "your-key.pem" ubuntu@<EC2_PUBLIC_IP>
```

### 3. Install Prerequisites
```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk curl wget git

# Verify Java
java -version  # Should be Java 21
```

### 4. Upload & Deploy
```bash
# From local machine - Upload JAR files
scp -i "your-key.pem" upload-service-main/build/libs/*.jar ubuntu@<EC2_PUBLIC_IP>:/tmp/
scp -i "your-key.pem" validation_service/build/libs/*.jar ubuntu@<EC2_PUBLIC_IP>:/tmp/

# Back on EC2 - Run deployment script
export MONGODB_PASSWORD="your-mongodb-password"
bash /tmp/deploy.sh
```

### 5. Verify Deployment
```bash
# Check services running
sudo systemctl status upload-service
sudo systemctl status validation-service

# Monitor logs
tail -f /opt/book-platform/logs/*.log
```

---

## 🔗 Service URLs

| Service | URL | Purpose |
|---------|-----|---------|
| Upload Service | `http://<EC2_IP>:8080/api/upload` | Upload manuscripts |
| Validation Service | `http://<EC2_IP>:8081/` | SQS consumer (internal) |
| Logs | `/opt/book-platform/logs/` | Application logs |

---

## 🔑 Environment Variables

```bash
# Set before running deploy.sh
export MONGODB_PASSWORD="your-password-here"
export AWS_REGION="ap-south-1"
export AWS_ACCESS_KEY=""      # Optional (use IAM role preferred)
export AWS_SECRET_KEY=""      # Optional (use IAM role preferred)
```

---

## 📊 Service Management

```bash
# View status
sudo systemctl status upload-service
sudo systemctl status validation-service

# Start/Stop/Restart
sudo systemctl start|stop|restart upload-service
sudo systemctl start|stop|restart validation-service

# View logs
journalctl -u upload-service -f
journalctl -u validation-service -f
tail -f /opt/book-platform/logs/upload-service.log
tail -f /opt/book-platform/logs/validation-service.log

# View running processes
ps aux | grep java
netstat -tlnp | grep java
```

---

## 🧪 Testing

### Test Upload Service
```bash
curl -X POST http://<EC2_IP>:8080/api/upload \
  -F "file=@test.pdf" \
  -F "bookId=BOOK-001" \
  -F "authorId=AUTH-001" \
  -F "requestId=REQ-001"
```

### Test Validation Service (via SQS)
```bash
aws sqs send-message \
  --queue-url https://sqs.ap-south-1.amazonaws.com/206465504931/manuscript-validation-queue \
  --message-body '{
    "eventType": "FILE_UPLOADED",
    "requestId": "REQ-TEST-001",
    "bookId": "BOOK-001",
    "bookName": "Test",
    "authorId": "AUTH-001",
    "authorEmail": "test@example.com",
    "fileFormat": "pdf",
    "isbn": "9789396055023",
    "s3Reference": "injection/REQ-TEST-001/test.pdf"
  }' \
  --region ap-south-1
```

---

## 🔐 Security Considerations

1. **Use IAM Roles** - Don't hardcode credentials
2. **Restrict Security Group** - Only allow needed ports
3. **Keep Systems Updated** - Run `sudo apt-get upgrade`
4. **Monitor Logs** - Watch for errors and anomalies
5. **Use SSL/TLS** - Set up Nginx reverse proxy (optional)
6. **Backup Data** - MongoDB and S3 buckets
7. **Monitor AWS CloudWatch** - Set up alarms

---

## 🆘 Common Issues & Fixes

| Issue | Solution |
|-------|----------|
| Port 8080 already in use | `sudo lsof -i :8080` then kill process |
| MongoDB connection failed | Check credentials in config file |
| SQS/SNS not working | Verify IAM role has required permissions |
| Out of memory | Increase heap: `-Xmx2048m` in systemd file |
| Services not starting | Check logs: `journalctl -u upload-service -f` |

---

## 📈 Performance Tuning

```bash
# Increase heap memory
sudo nano /etc/systemd/system/upload-service.service
# Change: ExecStart=/usr/bin/java -Xmx2048m -Xms1024m ...

# Reload and restart
sudo systemctl daemon-reload
sudo systemctl restart upload-service
```

---

## 🎯 Next Steps

1. ✅ Build JAR files
2. ✅ Launch EC2 instance
3. ✅ Install Java 21
4. ✅ Run deployment script
5. ✅ Monitor logs for errors
6. ✅ Test endpoints
7. ✅ Set up CloudWatch alarms
8. ✅ Configure auto-scaling (optional)

---

## 📞 Useful Commands

```bash
# SSH into EC2
ssh -i "key.pem" ubuntu@<IP>

# Transfer files
scp -i "key.pem" local-file ubuntu@<IP>:/remote/path
scp -i "key.pem" ubuntu@<IP>:/remote/file local-path

# Monitor services
watch -n 1 'ps aux | grep java'
watch -n 5 'curl -s http://localhost:8080 | head -20'

# View disk usage
df -h
du -sh /opt/book-platform

# View memory usage
free -h

# View network connections
netstat -tlnp
ss -tlnp
```

---

## 📚 Full Documentation

See `EC2_DEPLOYMENT_GUIDE.md` for detailed step-by-step instructions.
