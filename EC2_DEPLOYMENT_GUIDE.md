# AWS EC2 Deployment Guide - Manuscript Validation Service

## 📋 Project Overview

**Spring Boot Microservice:**
- **validation-service** - SQS consumer for manuscript validation with email notifications

**Note:** Upload service is already deployed by colleague and running on port 8080.

---

## � Understanding the Big Picture: Why Each Step is Required

### The Problem We're Solving
You have two Java Spring Boot applications that need to run 24/7 on a server. They communicate with AWS services (S3, SQS, SNS, SES) and MongoDB. We need to deploy them to AWS EC2 so they can run independently from your local machine.

### The Solution Flow
```
Local Machine          →    AWS EC2 Instance         →    AWS Services
(Build validation JAR) → (Copy to EC2) → (Run Java) → (Access S3, SQS, SNS, SES, MongoDB)
```

### Why AWS EC2?
```
❌ Running on Local Machine:
  - Depends on you keeping your computer running
  - No internet outage protection
  - Limited by your network
  - Can't scale automatically
  - No automatic restart on crash

✅ Running on AWS EC2:
  - Always on (24/7/365)
  - Auto-restart on crashes
  - Can scale to handle more traffic
  - Professional data center infrastructure
  - Pay only for what you use
```

### What This Service Does
```
Validation Service (Port 8081):
  → Polls SQS queue for new manuscripts (uploaded by colleague's upload-service)
  → Runs 6 validators (metadata, extension, size, integrity, existence, file name)
  → Stores results in MongoDB
  → Sends email notifications via SES to authors
  → Publishes validation results to SNS topic

Supporting AWS Services (Pre-configured):
  → S3: File storage in archive/ bucket (upload-service writes, validation reads)
  → SQS: manuscript-validation-queue (upload-service publishes, validation consumes)
  → SNS: manuscript-validation-result topic (validation publishes results)
  → SES: Email delivery (validation sends notifications)
  → MongoDB Atlas: book_processing_requests collection for storing validation results
```

---

## �🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       AWS EC2 Instance                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────┐        ┌──────────────────────┐      │
│  │   Upload Service     │        │  Validation Service  │      │
│  │   (Port 8080)        │        │   (Port 8081)        │      │
│  │   REST API           │        │   SQS Consumer       │      │
│  └──────────────────────┘        └──────────────────────┘      │
│            │                              │                    │
│            ↓                              ↓                    │
│  ┌─────────────────────────────────────────────────┐           │
│  │         AWS Services                            │           │
│  │  • S3 (Upload & Archive Buckets)                │           │
│  │  • SQS (manuscript-validation-queue)            │           │
│  │  • SNS (manuscript-validation-result topic)     │           │
│  │  • SES (Email notifications)                    │           │
│  │  • MongoDB Atlas (book_processing_requests)     │           │
│  └─────────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Prerequisites

### On Your Local Machine (Before Deployment)
```bash
# 1. Build validation service
cd validation_service
./gradlew clean build -x test

# Note: Upload service already deployed by colleague

# 2. Locate the validation service JAR file
# - validation_service/build/libs/validation-service-*.jar
```

### AWS Setup (Required)
1. **EC2 Instance**: t3.medium or larger (Ubuntu 22.04 LTS recommended)
2. **Security Group**: Rule for port 8081 (8080 already configured by colleague for upload-service)
3. **IAM Role**: Attached to EC2 for S3, SQS, SNS, SES, CloudWatch access
4. **MongoDB Atlas**: Cluster accessible from EC2 (shared with upload-service)
5. **S3 Bucket**: Archive bucket (upload-service writes files, validation reads them)
6. **SQS Queue**: `manuscript-validation-queue` (ap-south-1) - messages from upload-service
7. **SNS Topic**: `manuscript-validation-result` (ap-south-1) - publish validation results
8. **SES**: Verified email sender for sending validation notifications

---

## 📦 EC2 Instance Setup

### Step 1: Launch EC2 Instance

**What It Does:**
- Creates a new virtual server in AWS cloud
- Allocates computing resources (CPU, RAM, storage)
- Assigns a public IP address for access

**Why It's Required:**
- We need a server to run the Java applications
- Local machine won't work - needs to be on AWS cloud for SQS, SNS, S3 integration
- EC2 = Elastic Compute Cloud (AWS's virtual machine service)

**Configuration Details:**
```bash
# Instance Details:
# - AMI: Ubuntu 22.04 LTS (ami-0c55b159cbfafe1f0)
# - Instance Type: t3.medium (2 vCPU, 4 GB RAM)
# - VPC: Default or custom VPC
# - Security Group: Create with rules below

Why t3.medium:
  - Small services: t3.small (not enough memory for both)
  - Our services: t3.medium (good for development/small production)
  - High traffic: t3.large or t3.xlarge (upgrade later if needed)
```

**Timeline:** ~5 minutes

---

### Step 2: Security Group Rules

**What It Does:**
- Creates firewall rules for the EC2 instance
- Controls which ports/IPs can access the server
- Acts like a network access control list

**Why It's Required:**
- Without it, instance is locked down (can't access anything)
- Need to open specific ports for services to be reachable
- Prevents unauthorized access from internet

**Rules Needed:**
```
Inbound Rules:
  - SSH (22): 0.0.0.0/0 (restrict to your IP) → SSH into server for management
  - HTTP (80): 0.0.0.0/0 → Web traffic (optional Nginx proxy)
  - Custom TCP (8081): 0.0.0.0/0 → Validation Service endpoint

Outbound Rules:
  - All traffic allowed (AWS services, MongoDB)

Note: Port 8080 (Upload Service) is already configured by colleague

Why Each Port:
  - 22 (SSH): You need to login to server to manage it
  - 80 (HTTP): For Nginx reverse proxy (optional, production setup)
  - 8081: Validation Service (SQS consumer, can be internal-only)
```

**Timeline:** ~2 minutes

---

### Step 3: Create/Attach IAM Role

**What It Does:**
- Creates permissions profile attached to EC2
- Allows EC2 to access AWS services without hardcoding credentials
- Securely grants access to S3, SQS, SNS, SES

**Why It's Required:**
- Applications need to authenticate with AWS services
- Bad approach: Store access keys in config files (security risk)
- Good approach: Use IAM role (credentials auto-rotated, can't be exposed)
- Instance can use role = credentials auto-provided via metadata service

**Permissions Needed:**
```
AmazonS3FullAccess
  → Read/Write S3 buckets (upload, download, archive files)

AmazonSQSFullAccess
  → Consume messages from SQS queue
  → Delete messages after processing

AmazonSNSFullAccess
  → Publish validation results to SNS topic

AmazonSESFullAccess
  → Send email notifications to authors
  → Check verified emails and quota

Why Not Hardcode Credentials:
  ❌ Bad: Credentials exposed in code, logs, Git
  ✅ Good: Credentials provided via instance metadata, auto-rotated
```

**Timeline:** ~5 minutes

---

### Step 4: Connect to EC2 Instance

**What It Does:**
```bash
ssh -i "your-key.pem" ubuntu@<EC2_PUBLIC_IP>
```

**Why It's Required:**
- You need terminal access to the server
- SSH = Secure Shell (encrypted remote terminal)
- `your-key.pem` = private key matching public key on EC2
- `ubuntu` = default username for Ubuntu AMI

**Timeline:** ~10 seconds

---

## 🔧 Install Java and Dependencies

### Step 5: Update System and Install Java 21

**What It Does:**
```bash
# SSH into EC2
ssh -i "your-key.pem" ubuntu@<EC2_PUBLIC_IP>

# Update system packages
sudo apt-get update && sudo apt-get upgrade -y

# Install Java 21 LTS
sudo apt-get install -y openjdk-21-jdk

# Verify installation
java -version
# Output: openjdk version "21.0.x" ...

# Install other utilities
sudo apt-get install -y curl wget git
```

**Why It's Required:**
- Ubuntu comes with outdated security patches
- `update` = refresh package list from repositories
- `upgrade` = install latest versions of all packages
- Security fixes prevent hacking

**Why Java 21 Specifically:**
- Your Spring Boot applications are compiled Java bytecode
- JVM (Java Virtual Machine) = runtime environment
- Bytecode is platform-independent, JVM runs it on any OS
- Java 21 = LTS release, security supported until 2031

**What Gets Installed:**
```
openjdk-21-jdk includes:
  - JVM (Java Virtual Machine) - executes bytecode
  - JDK (Java Development Kit) - tools, libraries
  - java - Java runtime (this is what runs your JAR)
```

**Timeline:** ~2-3 minutes

---

### Step 6: Create Application Directory

**What It Does:**
```bash
# Create app directory
sudo mkdir -p /opt/book-platform
sudo chown ubuntu:ubuntu /opt/book-platform

# Create subdirectories
mkdir -p /opt/book-platform/validation-service
mkdir -p /opt/book-platform/logs

# Verify
ls -la /opt/book-platform
```

**Why It's Required:**
- Validation service needs a home directory
- Organized structure makes management easier
- Logs directory for application output
- Upload service already deployed elsewhere

**Why `/opt/`:**
```
Linux directory conventions:
  /bin → System executables
  /home → User home directories
  /opt → Optional software packages (what we use)
  /usr → User programs and libraries
```

**Structure Created:**
```
/opt/book-platform/
├── validation-service/   → Validation Service files
└── logs/                 → Log files
```

**Timeline:** ~5 seconds

---

## 📤 Upload JAR File to EC2

### Step 7: Transfer Validation Service JAR (From Local Machine)

**What It Does:**
```bash
# From your local machine
# Upload validation-service JAR
scp -i "your-key.pem" \
    validation_service/build/libs/validation-service-*.jar \
    ubuntu@<EC2_PUBLIC_IP>:/opt/book-platform/validation-service/

# Verify on EC2
ssh -i "your-key.pem" ubuntu@<EC2_PUBLIC_IP>
ls -la /opt/book-platform/validation-service/
```

**Why It's Required:**
- Validation service built locally, needs to move to EC2
- JAR file is self-contained (includes all dependencies)
- SCP = Secure Copy (SSH-based file transfer)
- Transfer over secure channel (encrypted)
- Upload service already deployed by colleague

**What Gets Copied:**
```
Local: ~/Projects/validation_service/build/libs/validation-service-0.0.1-SNAPSHOT.jar (63.1 MB)
  ↓ (scp)
Remote: /opt/book-platform/validation-service/
```

**Why Not Use Git:**
- Git intended for source code, not 60+ MB binaries
- Clutters repository history
- Unnecessary bandwidth
- Direct copy is faster and cleaner

**Timeline:** ~1-2 minutes (63.1 MB)

---

## ⚙️ Configuration Files

### Step 8: Create Validation Service Configuration File

**What It Does & Why It's Required:**
- JAR file is generic (doesn't know about AWS, MongoDB, ports)
- Configuration file tells application WHERE/HOW to connect
- Different config for dev, staging, production
- Separates code from configuration (security best practice)

**On EC2: Validation Service Configuration**
```bash
cat > /opt/book-platform/validation-service/application.yaml << 'EOF'
server:
  port: 8081
spring:
  application:
    name: validation-service
  data:
    mongodb:
      uri: mongodb+srv://taskadmin:YOUR_PASSWORD@cluster0.02zhddb.mongodb.net/book_management
      database: book_management

aws:
  region: ap-south-1
  s3:
    bucket-name: book-platform-files-206465504931-ap-south-1-an
    archive-bucket-name: s3://book-platform-files-206465504931-ap-south-1-an/archive/
  sqs:
    queue-name: manuscript-validation-queue
    endpoint: https://sqs.ap-south-1.amazonaws.com
  sns:
    validation-result-topic-arn: arn:aws:sns:ap-south-1:206465504931:manuscript-validation-result
  ses:
    from-email: vishakhamore2828@gmail.com

camel:
  springboot:
    name: validation-service-route

logging:
  level:
    root: INFO
    com.manuscriptvalidation: DEBUG
  file:
    name: /opt/book-platform/logs/validation-service.log
    max-size: 10MB
    max-history: 10
EOF
```

---

## 🔐 AWS Credentials Setup

### Step 9: Configure AWS Credentials on EC2

**What It Does & Why It's Required:**
- Applications need to authenticate with AWS services
- AWS SDK needs to know: "Who am I?"
- Instance Profile = automatic, secure credential rotation
- CLI config = manual but works if no instance profile

**How AWS SDK Gets Credentials:**
```
Application starts
  ↓
AWS SDK runs credential provider chain:
  1. Check environment variables (AWS_ACCESS_KEY_ID, etc.)
  2. Check ~/.aws/credentials file
  3. Check ~/.aws/config file
  4. Call instance metadata service (http://169.254.169.254/...)
  5. If any work → Use those credentials
  6. If none work → Throw exception
```

**Option A: Using IAM Instance Profile (Recommended)**
```bash
# 1. Create IAM Role in AWS Console with policies:
#    - AmazonS3FullAccess (or custom S3 policy)
#    - AmazonSQSFullAccess (or custom SQS policy)
#    - AmazonSNSFullAccess (or custom SNS policy)
#    - AmazonSESFullAccess (or custom SES policy)

# 2. Attach role to EC2 instance
# 3. Applications will auto-detect credentials from instance metadata

# Verify on EC2:
curl http://169.254.169.254/latest/meta-data/iam/security-credentials/

Why Instance Profile is Best:
  ✅ Credentials auto-rotated every 6 hours
  ✅ Can't be exposed in code/logs/config
  ✅ Can revoke access instantly via IAM
  ✅ Works automatically, no setup needed
```

**Option B: Using AWS CLI Credentials**
```bash
# Install AWS CLI
sudo apt-get install -y awscli

# Configure credentials
aws configure
# Enter:
# - AWS Access Key ID: YOUR_ACCESS_KEY
# - AWS Secret Access Key: YOUR_SECRET_KEY
# - Default region: ap-south-1
# - Default output format: json

# Credentials stored in: ~/.aws/credentials

❌ Hardcoded Credentials Problems:
  - Fixed, never rotates
  - Exposed if code leaked
  - Must manually rotate
  - Visible in logs/error messages
```

**Timeline:** ~30 seconds

---

## 🚀 Start Validation Service

### Step 10: Start Validation Service

**What It Does:**
```bash
# Navigate to directory
cd /opt/book-platform/validation-service

# Run JAR
nohup java -jar validation-service-*.jar > /opt/book-platform/logs/validation-service-startup.log 2>&1 &

# Verify running
sleep 5
ps aux | grep java

# Check logs
tail -f /opt/book-platform/logs/validation-service.log
```

**Timeline:** ~5 seconds

---

### Step 11: Verify Validation Service Running

**What It Does & Why It's Required:**
- Verify service actually started (not just configuration)
- Check port is listening for connections
- Early detection of startup errors
- Validates entire deployment chain

```bash
# Check process status
ps aux | grep java | grep -v grep

# Check port listening
netstat -tlnp | grep java
# Should show:
# - 0.0.0.0:8081 (validation-service)

# Check logs for any errors
tail -f /opt/book-platform/logs/validation-service.log
```

**What Each Command Reveals:**
```
ps aux | grep java
  Shows:
    - Full Java command line
    - Memory allocated (-Xmx2048m)
    - JAR file name

netstat -tlnp | grep java
  Shows:
    - Port 8081 listening (0.0.0.0:8081)
    - Protocol (tcp)
```

**Timeline:** ~10 seconds

---

## 🔄 Create Systemd Service (Auto-start on Reboot)

### Step 12: Create Validation Service Systemd File

**What It Does & Why It's Required:**
- Systemd = Linux system daemon manager
- Manages service lifecycle (start, stop, restart, enable)
- Auto-starts service on server reboot
- Auto-restarts if service crashes
- Captures logs for debugging

**Why Systemd Instead of Manual:**
```
❌ Manual:
  sudo java -jar validation-service-*.jar
  → Blocks terminal
  → Stops if terminal closes
  → Need to manually restart on reboot
  → No automatic restart if crashes

✅ Systemd:
  sudo systemctl start validation-service
  → Runs in background
  → Survives SSH disconnect
  → Auto-starts on server reboot
  → Auto-restarts if crashes
```

**Create the Service File:**

```bash
sudo tee /etc/systemd/system/validation-service.service > /dev/null << 'EOF'
[Unit]
Description=Book Platform Validation Service
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/book-platform/validation-service
ExecStart=/usr/bin/java -Xmx2048m -Xms1024m -jar validation-service-*.jar
Restart=always
RestartSec=10
StandardOutput=append:/opt/book-platform/logs/validation-service.log
StandardError=append:/opt/book-platform/logs/validation-service-error.log
Environment="AWS_DEFAULT_REGION=ap-south-1"

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable validation-service
sudo systemctl start validation-service
sudo systemctl status validation-service
```

**What Each Section Does:**
```
[Unit]
Description=Book Platform Validation Service
  → Human-readable name for the service

After=network.target
  → Start after network is ready

[Service]
Type=simple
  → Service type (simple = foreground process)

User=ubuntu
  → Run service as ubuntu user (not root)
  → Security: services run with minimal privileges

WorkingDirectory=/opt/book-platform/validation-service
  → Change to this directory before running

ExecStart=/usr/bin/java -Xmx2048m -Xms1024m -jar validation-service-*.jar
  → Command to start service
  → -Xmx2048m = max heap 2GB (memory limit)
  → -Xms1024m = initial heap 1GB (startup memory)

Restart=always
  → If service crashes, restart it
  → Keeps service running 24/7

RestartSec=10
  → Wait 10 seconds before restarting crashed service

StandardOutput/Error=append:...
  → Redirect logs to files
  → append = add to existing file (not overwrite)

[Install]
WantedBy=multi-user.target
  → Enable service in multi-user runlevel
  → Makes it auto-start on boot
```

**Timeline:** ~10 seconds

---

## 📊 Monitoring & Logging

### Step 13: View Logs
```bash
# Real-time validation service logs
tail -f /opt/book-platform/logs/validation-service.log

# Last 50 lines of logs
tail -50 /opt/book-platform/logs/validation-service.log
```

### Step 14: Service Management
```bash
# Check status
sudo systemctl status validation-service

# Restart service
sudo systemctl restart validation-service

# Stop service
sudo systemctl stop validation-service

# View service logs (journalctl)
journalctl -u validation-service -f
```

---

## 🧪 Testing

### Step 15: Test Validation Service (SQS Integration)
```bash
# Send test message to SQS queue using AWS CLI
aws sqs send-message \
  --queue-url https://sqs.ap-south-1.amazonaws.com/206465504931/manuscript-validation-queue \
  --message-body '{
    "eventType": "FILE_UPLOADED",
    "requestId": "REQ-TEST-001",
    "bookId": "BOOK-001",
    "bookName": "Test Manuscript",
    "authorId": "AUTH-001",
    "authorEmail": "test@example.com",
    "fileFormat": "pdf",
    "isbn": "9789396055023",
    "s3Reference": "injection/REQ-TEST-001/test.pdf"
  }' \
  --region ap-south-1

# Check validation service logs
tail -f /opt/book-platform/logs/validation-service.log
```

---

## 🔐 Security Best Practices

### Step 16: Security Hardening
```bash
# 1. Restrict SSH access
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 8081/tcp
sudo ufw enable

# Note: Port 8080 already configured by colleague for upload-service

# 2. Regular updates
sudo apt-get update && sudo apt-get upgrade -y

# 3. Monitor logs for errors
tail -f /opt/book-platform/logs/validation-service.log

# 4. Use SSL/TLS (with nginx reverse proxy recommended)
# See optional nginx setup section below

# 5. Keep AWS credentials in IAM roles (not in files)
# Never commit credentials to Git

# 6. Monitor CloudWatch metrics
# S3, SQS, SNS, SES all provide metrics
```

---

## 🔄 Reverse Proxy Setup (Optional - Nginx)

### Step 17: Install and Configure Nginx (Optional)
```bash
# Install Nginx
sudo apt-get install -y nginx

# Create Nginx configuration
sudo tee /etc/nginx/sites-available/book-platform > /dev/null << 'EOF'
upstream validation_service {
    server localhost:8081;
}

server {
    listen 80;
    server_name <EC2_PUBLIC_IP>;

    # Validation Service
    location /validation {
        proxy_pass http://validation_service;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Note: Upload service (port 8080) is on separate server/instance
}
EOF

# Enable site
sudo ln -s /etc/nginx/sites-available/book-platform /etc/nginx/sites-enabled/

# Test configuration
sudo nginx -t

# Start Nginx
sudo systemctl enable nginx
sudo systemctl start nginx
sudo systemctl status nginx
```

---

## 📋 Deployment Checklist

- [ ] EC2 instance created and running (Ubuntu 22.04)
- [ ] Security group rules configured (SSH, 8081, optional 80)
- [ ] IAM role attached to EC2 with required permissions
- [ ] Java 21 installed on EC2
- [ ] Validation service directory created
- [ ] Validation service JAR uploaded to EC2
- [ ] Configuration file (application.yaml) created with correct credentials
- [ ] AWS credentials configured (IAM role or CLI)
- [ ] Validation service systemd service created and running
- [ ] Validation service verified running on port 8081
- [ ] MongoDB connection verified
- [ ] S3 bucket accessible (archive folder for reading)
- [ ] SQS queue accessible (consuming from manuscript-validation-queue)
- [ ] SNS topic accessible (publishing to manuscript-validation-result)
- [ ] SES email verified
- [ ] Service set to auto-start on reboot
- [ ] Logs configured and monitored
- [ ] Firewall rules applied (ufw)
- [ ] Nginx reverse proxy configured (optional)
- [ ] Coordination complete with colleague's upload-service deployment

---

## 🆘 Troubleshooting

### Service Won't Start
```bash
# Check logs
tail -f /opt/book-platform/logs/validation-service.log

# Common issues:
# 1. Port 8081 already in use: Check `netstat -tlnp`
# 2. Insufficient memory: Increase heap size in systemd file (-Xmx flag)
# 3. Missing JAR: Verify file exists: `ls -la /opt/book-platform/validation-service/`
# 4. Configuration file issues: Check application.yaml syntax
# 5. MongoDB connection: Verify credentials and IP whitelist in MongoDB Atlas
```

### AWS Credentials Error
```bash
# Verify IAM role attached
aws sts get-caller-identity

# Check credentials
cat ~/.aws/credentials

# Verify permissions in IAM role
# Console: EC2 → Instances → Instance Details → IAM Role
```

### Database Connection Error
```bash
# Verify MongoDB connection string
# Test from EC2: nc -zv cluster0.02zhddb.mongodb.net 27017

# Common issues:
# 1. Wrong password in connection string
# 2. IP not whitelisted in MongoDB Atlas
# 3. Database name incorrect
```

### SQS/SNS Not Working
```bash
# Verify queue/topic exists
aws sqs list-queues --region ap-south-1
aws sns list-topics --region ap-south-1

# Verify IAM permissions
# Role should have: sqs:*, sns:*, s3:*, ses:*
```

---

## 🎯 Next Steps

1. **Build JAR file locally** (validation_service only)
2. **Launch EC2 instance** with Ubuntu 22.04 LTS
3. **Follow Steps 1-12** to set up validation service
4. **Verify coordination** with colleague's upload-service on same/different instance
5. **Monitor logs** to verify validation service is running and consuming SQS messages
6. **Test SQS integration** by sending test messages from upload-service
7. **Verify email notifications** are being sent to authors
8. **Set up CloudWatch alarms** for production monitoring
9. **Configure backups** for database and S3 data

---

## 📞 Support

For AWS CLI setup: `aws configure help`
For Java troubleshooting: Check systemd journal `journalctl -u validation-service -f`
For MongoDB: Visit MongoDB Atlas console (coordinate with colleague)
For SES: Check SES sandbox status and verified emails
For SQS/SNS: Coordinate with colleague to ensure upload-service is configured correctly
For coordination: Ensure both services share same MongoDB database, S3 bucket, SQS queue, and SNS topic

