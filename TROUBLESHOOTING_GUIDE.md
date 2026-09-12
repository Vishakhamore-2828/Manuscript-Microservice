# EC2 Deployment Troubleshooting Guide

## 🔧 Common Issues & Solutions

---

## 1. Services Won't Start

### Issue: Upload Service or Validation Service not starting
```
Error: Job for upload-service.service failed
```

### Diagnosis
```bash
# Check service status
sudo systemctl status upload-service -l

# View service logs
journalctl -u upload-service -n 50 -e

# Check if port is in use
sudo lsof -i :8080

# Manually run JAR to see errors
cd /opt/book-platform/upload-service
java -jar upload-service-*.jar
```

### Solutions

**A. Port Already in Use**
```bash
# Find process using port 8080
sudo lsof -i :8080

# Kill the process
sudo kill -9 <PID>

# Restart service
sudo systemctl restart upload-service
```

**B. JAR File Not Found**
```bash
# Verify JAR exists
ls -la /opt/book-platform/upload-service/

# If missing, upload JAR again
scp -i "key.pem" upload-service-*.jar ubuntu@<IP>:/opt/book-platform/upload-service/

# Restart
sudo systemctl restart upload-service
```

**C. Insufficient Heap Memory**
```bash
# Edit systemd file
sudo nano /etc/systemd/system/upload-service.service

# Change line (increase memory):
# ExecStart=/usr/bin/java -Xmx2048m -Xms1024m -jar upload-service-*.jar

# Save (Ctrl+O, Ctrl+X)
sudo systemctl daemon-reload
sudo systemctl restart upload-service
```

**D. Configuration File Issues**
```bash
# Check application.properties syntax
cat /opt/book-platform/upload-service/application.properties

# Verify MongoDB password is correct
# Verify S3 bucket name is correct
# Verify AWS region is set

# Fix and restart
sudo systemctl restart upload-service
```

---

## 2. MongoDB Connection Failed

### Issue
```
MongoException: connect failed
com.mongodb.MongoSecurityException: Exception authenticating MongoCredential
```

### Diagnosis
```bash
# Check if MongoDB connection string is correct
grep "mongodb" /opt/book-platform/upload-service/application.properties

# Test connection manually
nc -zv cluster0.02zhddb.mongodb.net 27017

# Verify network connectivity
curl -v https://cluster0.02zhddb.mongodb.net
```

### Solutions

**A. Wrong Password**
```bash
# Edit configuration
sudo nano /opt/book-platform/upload-service/application.properties

# Find line: spring.data.mongodb.uri=mongodb+srv://taskadmin:PASSWORD@...
# Update PASSWORD

# Verify the password is correct in MongoDB Atlas
# Restart service
sudo systemctl restart upload-service
```

**B. IP Not Whitelisted in MongoDB Atlas**
```bash
# 1. Go to MongoDB Atlas Console
# 2. Click Network Access
# 3. Add current EC2 public IP to whitelist
# 4. Wait 5-10 minutes for changes to apply
# 5. Restart service
sudo systemctl restart upload-service
```

**C. Database Name Incorrect**
```bash
# Verify database name
grep "mongodb.database" /opt/book-platform/upload-service/application.properties

# Should be: book_management
# If incorrect, update and restart
sudo systemctl restart upload-service
```

**D. Connection Timeout**
```bash
# Increase connection timeout in config
sudo nano /opt/book-platform/upload-service/application.properties

# Add lines:
# spring.data.mongodb.uri=mongodb+srv://taskadmin:...?maxPoolSize=100&minPoolSize=0&maxWaitTimeMS=120000

# Restart
sudo systemctl restart upload-service
```

---

## 3. AWS Credentials Error

### Issue
```
Unable to load credentials from any of the providers in the chain
AWS_ACCESS_KEY_ID not set
```

### Diagnosis
```bash
# Check IAM role attached to instance
aws sts get-caller-identity

# List available credentials
aws configure list

# Check credential providers
env | grep AWS
```

### Solutions

**A. Using IAM Instance Profile (Recommended)**
```bash
# 1. Stop services
sudo systemctl stop upload-service validation-service

# 2. Go to AWS Console → EC2 → Instances
# 3. Select instance → Instance State → Modify IAM instance profile
# 4. Attach role with permissions:
#    - AmazonS3FullAccess
#    - AmazonSQSFullAccess
#    - AmazonSNSFullAccess
#    - AmazonSESFullAccess

# 5. Wait 30 seconds for metadata to update
# 6. Verify credentials
aws sts get-caller-identity

# 7. Restart services
sudo systemctl start upload-service validation-service
```

**B. Using AWS CLI Credentials**
```bash
# Configure credentials
aws configure

# Enter:
# AWS Access Key ID: YOUR_KEY
# AWS Secret Access Key: YOUR_SECRET
# Default region name: ap-south-1
# Default output format: json

# Verify
aws sts get-caller-identity

# Restart services
sudo systemctl restart upload-service validation-service
```

**C. Test Specific AWS Services**
```bash
# Test S3 access
aws s3 ls

# Test SQS access
aws sqs list-queues --region ap-south-1

# Test SNS access
aws sns list-topics --region ap-south-1

# Test SES
aws ses list-verified-email-addresses --region ap-south-1
```

---

## 4. S3 Upload/Download Failures

### Issue
```
403 Forbidden: Signature mismatch
S3Exception: Access Denied
```

### Diagnosis
```bash
# Check S3 bucket access
aws s3 ls s3://book-platform-files-206465504931-ap-south-1-an/

# List bucket contents
aws s3 ls s3://book-platform-files-206465504931-ap-south-1-an/ --recursive

# Check specific path
aws s3 ls s3://book-platform-files-206465504931-ap-south-1-an/injection/
```

### Solutions

**A. S3 Bucket Name Incorrect**
```bash
# Edit configuration
sudo nano /opt/book-platform/upload-service/application.properties

# Check: aws.s3.bucket-name=
# Should be: book-platform-files-206465504931-ap-south-1-an
# Without trailing slash or "s3://" prefix

# Restart
sudo systemctl restart upload-service
```

**B. IAM Permissions Missing**
```bash
# 1. Go to AWS Console → IAM → Roles
# 2. Find role attached to EC2
# 3. Add inline policy or attach AmazonS3FullAccess
# 4. Policy should allow:
#    - s3:GetObject
#    - s3:PutObject
#    - s3:DeleteObject
#    - s3:ListBucket

# 5. Wait for changes to apply (1-2 minutes)
# 6. Restart services
sudo systemctl restart upload-service validation-service
```

**C. Bucket Region Mismatch**
```bash
# Verify bucket region
aws s3api get-bucket-location --bucket book-platform-files-206465504931-ap-south-1-an

# Should be: ap-south-1
# Update config if different
sudo nano /opt/book-platform/upload-service/application.properties
# Set: aws.region=ap-south-1

# Restart
sudo systemctl restart upload-service
```

---

## 5. SQS Queue Not Receiving Messages

### Issue
```
Unable to consume from SQS queue
Queue not found or not accessible
```

### Diagnosis
```bash
# Check queue exists
aws sqs list-queues --region ap-south-1

# Get queue URL
aws sqs get-queue-url --queue-name manuscript-validation-queue --region ap-south-1

# Check queue attributes
aws sqs get-queue-attributes \
  --queue-url https://sqs.ap-south-1.amazonaws.com/206465504931/manuscript-validation-queue \
  --attribute-names All \
  --region ap-south-1

# Send test message
aws sqs send-message \
  --queue-url https://sqs.ap-south-1.amazonaws.com/206465504931/manuscript-validation-queue \
  --message-body '{"test": "message"}' \
  --region ap-south-1
```

### Solutions

**A. Queue Not Found**
```bash
# Create queue if missing
aws sqs create-queue \
  --queue-name manuscript-validation-queue \
  --region ap-south-1

# Get queue URL
aws sqs get-queue-url --queue-name manuscript-validation-queue --region ap-south-1
```

**B. Missing SQS Permissions**
```bash
# Add to IAM role:
# - sqs:ReceiveMessage
# - sqs:DeleteMessage
# - sqs:GetQueueAttributes
# - sqs:ListQueues

# Restart services
sudo systemctl restart validation-service
```

**C. Queue URL Wrong in Config**
```bash
# Check config
grep "sqs" /opt/book-platform/validation-service/application.yaml

# Should reference: manuscript-validation-queue
# AWS SDK will auto-construct URL

# Restart
sudo systemctl restart validation-service
```

---

## 6. SNS Topic Not Receiving Messages

### Issue
```
Failed to publish to SNS
TopicNotFound or Access Denied
```

### Diagnosis
```bash
# List topics
aws sns list-topics --region ap-south-1

# Get topic details
aws sns get-topic-attributes \
  --topic-arn arn:aws:sns:ap-south-1:206465504931:manuscript-validation-result \
  --region ap-south-1

# Publish test message
aws sns publish \
  --topic-arn arn:aws:sns:ap-south-1:206465504931:manuscript-validation-result \
  --message "Test message" \
  --region ap-south-1
```

### Solutions

**A. Topic Doesn't Exist**
```bash
# Create topic
aws sns create-topic \
  --name manuscript-validation-result \
  --region ap-south-1
```

**B. Wrong Topic ARN in Config**
```bash
# Edit config
sudo nano /opt/book-platform/validation-service/application.yaml

# Check line: validation-result-topic-arn:
# Should be: arn:aws:sns:ap-south-1:206465504931:manuscript-validation-result

# Verify with AWS
aws sns list-topics --region ap-south-1

# Restart
sudo systemctl restart validation-service
```

**C. Missing SNS Permissions**
```bash
# IAM role needs:
# - sns:Publish
# - sns:ListTopics
# - sns:GetTopicAttributes

# Attach policy and restart
sudo systemctl restart validation-service
```

---

## 7. SES Email Not Sending

### Issue
```
MessageRejected: Email not verified
Sandbox mode restrictions
```

### Diagnosis
```bash
# Check verified email addresses
aws ses list-verified-email-addresses --region ap-south-1

# Check SES quota
aws ses get-account-sending-enabled --region ap-south-1

# Get send statistics
aws ses get-send-statistics --region ap-south-1
```

### Solutions

**A. Sender Email Not Verified**
```bash
# Check config
grep "from-email" /opt/book-platform/validation-service/application.yaml

# Should be verified in SES
# If not, verify it:
aws ses verify-email-identity \
  --email-address vishakhamore2828@gmail.com \
  --region ap-south-1

# Check verification
aws ses list-verified-email-addresses --region ap-south-1
```

**B. Recipient Email Not Verified (Sandbox Mode)**
```bash
# If in sandbox, both sender and recipient must be verified
# Current status: Sandbox mode

# Verify recipient
aws ses verify-email-identity \
  --email-address recipient@example.com \
  --region ap-south-1

# To exit sandbox, request production access:
# AWS Console → SES → Sending Limits → Request Sending Limit Increase
```

**C. Daily Quota Exceeded**
```bash
# Check quota status
aws ses get-send-quota --region ap-south-1

# Sandbox limit: 200 emails/day
# Wait until next day or request production access

# Monitor usage
aws ses get-send-statistics --region ap-south-1
```

**D. SES Sending Disabled**
```bash
# Check if sending is enabled
aws ses get-account-sending-enabled --region ap-south-1

# Should return: Enabled: true

# If disabled, enable it
# AWS Console → SES → Account Dashboard → Enable Sending
```

---

## 8. High Memory Usage

### Issue
```
Java heap space OutOfMemoryError
Services crashing due to memory
```

### Diagnosis
```bash
# Check memory usage
free -h

# Check Java process memory
ps aux | grep java | grep -v grep

# Monitor memory in real-time
watch -n 1 'free -h && echo "---" && ps aux | grep java | grep -v grep'
```

### Solutions

**A. Increase Heap Memory**
```bash
# Edit upload service
sudo nano /etc/systemd/system/upload-service.service

# Change line to:
# ExecStart=/usr/bin/java -Xmx2048m -Xms1024m -jar upload-service-*.jar

# Edit validation service
sudo nano /etc/systemd/system/validation-service.service

# Change line to:
# ExecStart=/usr/bin/java -Xmx3072m -Xms1536m -jar validation-service-*.jar

# Reload and restart
sudo systemctl daemon-reload
sudo systemctl restart upload-service validation-service
```

**B. Upgrade EC2 Instance**
```bash
# If still running out of memory:
# 1. Stop services
sudo systemctl stop upload-service validation-service

# 2. AWS Console → Stop EC2 instance
# 3. Right-click → Instance Settings → Change Instance Type
# 4. Select larger instance (t3.large or t3.xlarge)
# 5. Restart instance
# 6. Connect and restart services
sudo systemctl start upload-service validation-service
```

**C. Enable Garbage Collection Logging**
```bash
# Add GC logging to systemd file
sudo nano /etc/systemd/system/upload-service.service

# Add to ExecStart:
# -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:/opt/book-platform/logs/upload-gc.log

# Restart and monitor GC behavior
sudo systemctl daemon-reload
sudo systemctl restart upload-service
```

---

## 9. Services Crash on Reboot

### Issue
```
Services not running after EC2 reboot
Need to manually restart
```

### Solutions

**A. Enable Auto-Start**
```bash
# Verify auto-start enabled
sudo systemctl is-enabled upload-service
sudo systemctl is-enabled validation-service

# If not enabled, enable them
sudo systemctl enable upload-service
sudo systemctl enable validation-service

# Reboot to test
sudo reboot
```

**B. Check Startup Order**
```bash
# Edit systemd file
sudo nano /etc/systemd/system/upload-service.service

# Add after [Unit] section:
# After=network.target
# Wants=network-online.target

# Same for validation service

# Reload
sudo systemctl daemon-reload
sudo systemctl restart upload-service
```

---

## 10. High CPU Usage

### Issue
```
Services consuming 100% CPU
Validation service stuck processing
```

### Diagnosis
```bash
# Check CPU usage
top -b -n 1 | grep java

# Check thread count
ps -eLf | grep java | wc -l

# Monitor with top
top -p $(pgrep -f "java.*upload-service" | tr '\n' ',')
```

### Solutions

**A. Restart Service**
```bash
# Gracefully restart
sudo systemctl restart upload-service

# If stuck, force restart
sudo systemctl stop upload-service
sleep 5
sudo systemctl start upload-service
```

**B. Check for Infinite Loops**
```bash
# Increase log level temporarily
sudo nano /opt/book-platform/upload-service/application.properties

# Set: logging.level.root=DEBUG

# Monitor logs for repeating patterns
tail -f /opt/book-platform/logs/upload-service.log

# Look for patterns indicating infinite loops
```

**C. Tune Thread Pool**
```bash
# Edit config file
sudo nano /opt/book-platform/upload-service/application.properties

# Add:
# server.tomcat.threads.max=200
# server.tomcat.threads.min-spare=10
# server.tomcat.accept-count=100

# Restart
sudo systemctl restart upload-service
```

---

## 11. Disk Space Full

### Issue
```
No space left on device
Logs filling up disk
```

### Diagnosis
```bash
# Check disk usage
df -h

# Check directory sizes
du -sh /opt/book-platform/*

# Find large log files
find /opt/book-platform/logs -type f -exec du -sh {} \; | sort -h | tail -10
```

### Solutions

**A. Rotate Logs Manually**
```bash
# Compress old logs
gzip /opt/book-platform/logs/*.log.*

# Remove old compressed logs (older than 7 days)
find /opt/book-platform/logs -name "*.gz" -mtime +7 -delete

# Verify space freed
df -h
```

**B. Configure Log Rotation (logrotate)**
```bash
# Create logrotate config
sudo tee /etc/logrotate.d/book-platform > /dev/null << 'EOF'
/opt/book-platform/logs/*.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    create 0640 ubuntu ubuntu
}
EOF

# Test
sudo logrotate -f /etc/logrotate.d/book-platform

# Verify
ls -la /opt/book-platform/logs/
```

**C. Reduce Log Level**
```bash
# Edit config
sudo nano /opt/book-platform/upload-service/application.properties

# Change: logging.level.root=WARN
# or: logging.level.root=ERROR

# Restart
sudo systemctl restart upload-service
```

---

## 12. Network Connectivity Issues

### Issue
```
Unable to connect to service
Timeouts connecting to AWS services
```

### Diagnosis
```bash
# Check network connectivity
curl -v http://localhost:8080

# Check AWS connectivity
curl -v https://s3.ap-south-1.amazonaws.com

# Check DNS resolution
nslookup cluster0.02zhddb.mongodb.net

# Check security group
# AWS Console → Security Groups → Check inbound/outbound rules
```

### Solutions

**A. Update Security Group**
```bash
# AWS Console → Security Groups → Edit inbound rules
# Add:
# - SSH (22): Your IP
# - HTTP (80): 0.0.0.0/0
# - Custom TCP 8080: 0.0.0.0/0 (or restricted IP)
# - Custom TCP 8081: 0.0.0.0/0 (or restricted IP)

# Check outbound (should allow all or at least HTTPS)
```

**B. Check Network ACLs**
```bash
# AWS Console → VPC → Network ACLs
# Ensure inbound/outbound rules allow traffic

# Default should allow all, but verify
```

**C. Test Connectivity**
```bash
# Ping AWS service
ping google.com

# Test specific ports
nc -zv s3.ap-south-1.amazonaws.com 443
nc -zv cluster0.02zhddb.mongodb.net 27017
```

---

## 📊 Useful Commands Reference

```bash
# Service Management
sudo systemctl status upload-service
sudo systemctl restart upload-service
sudo systemctl stop upload-service
sudo systemctl start upload-service
sudo systemctl enable upload-service

# View Logs
tail -f /opt/book-platform/logs/upload-service.log
tail -200 /opt/book-platform/logs/upload-service.log
grep "ERROR" /opt/book-platform/logs/upload-service.log
journalctl -u upload-service -f

# Process Management
ps aux | grep java
kill -9 <PID>
sudo lsof -i :8080

# Network
netstat -tlnp | grep java
ss -tlnp
curl -v http://localhost:8080

# AWS CLI
aws sts get-caller-identity
aws s3 ls
aws sqs list-queues --region ap-south-1
aws sns list-topics --region ap-south-1

# System
df -h
free -h
top
watch -n 1 command
```

---

## 📋 Debugging Checklist

When service isn't working:

- [ ] Check service status: `sudo systemctl status <service>`
- [ ] View recent logs: `tail -50 /opt/book-platform/logs/*.log`
- [ ] Verify port is listening: `netstat -tlnp | grep 8080`
- [ ] Check JAR file exists: `ls -la /opt/book-platform/*/`
- [ ] Verify configuration: `cat /opt/book-platform/*/application.*`
- [ ] Test AWS credentials: `aws sts get-caller-identity`
- [ ] Check MongoDB connection: Look for connection errors in logs
- [ ] Verify memory: `free -h` and `ps aux | grep java`
- [ ] Check disk space: `df -h`
- [ ] Verify security group: AWS Console → Check inbound/outbound rules

---

## 🆘 When All Else Fails

```bash
# 1. Stop services
sudo systemctl stop upload-service validation-service

# 2. Check logs for clues
tail -100 /opt/book-platform/logs/*.log

# 3. Verify all prerequisites
java -version
aws sts get-caller-identity
ls -la /opt/book-platform/*/

# 4. Try running JAR directly to see errors
cd /opt/book-platform/upload-service
java -Xmx1024m -Xms512m -jar upload-service-*.jar

# 5. If still stuck:
#    - Check AWS Console for service limits
#    - Verify database is accessible
#    - Check security group rules
#    - Review application logs for specific error messages
#    - Contact AWS support if AWS service issue
```

