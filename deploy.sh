#!/bin/bash

# ============================================================================
# AWS EC2 Automated Deployment Script
# Manuscript Validation & Upload Services
# ============================================================================

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ============================================================================
# Configuration
# ============================================================================

APP_HOME="/opt/book-platform"
UPLOAD_SERVICE_DIR="$APP_HOME/upload-service"
VALIDATION_SERVICE_DIR="$APP_HOME/validation-service"
LOGS_DIR="$APP_HOME/logs"

AWS_REGION="ap-south-1"
MONGODB_PASSWORD="" # Set this before running
AWS_ACCESS_KEY=""   # Optional if using IAM role
AWS_SECRET_KEY=""   # Optional if using IAM role

# ============================================================================
# Functions
# ============================================================================

print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

check_command() {
    if command -v $1 &> /dev/null; then
        print_success "$1 is installed"
    else
        print_error "$1 is not installed"
        exit 1
    fi
}

# ============================================================================
# Step 1: Verify Prerequisites
# ============================================================================

verify_prerequisites() {
    print_header "Step 1: Verifying Prerequisites"
    
    check_command "java"
    check_command "curl"
    check_command "aws"
    
    # Verify Java version
    JAVA_VERSION=$(java -version 2>&1 | grep "openjdk" | head -1)
    if [[ $JAVA_VERSION == *"21"* ]]; then
        print_success "Java 21 is installed"
    else
        print_error "Java 21 is required, but $JAVA_VERSION found"
        exit 1
    fi
}

# ============================================================================
# Step 2: Create Directories
# ============================================================================

create_directories() {
    print_header "Step 2: Creating Directories"
    
    if [ ! -d "$APP_HOME" ]; then
        sudo mkdir -p "$APP_HOME"
        sudo chown $(whoami):$(whoami) "$APP_HOME"
        print_success "Created $APP_HOME"
    fi
    
    mkdir -p "$UPLOAD_SERVICE_DIR"
    mkdir -p "$VALIDATION_SERVICE_DIR"
    mkdir -p "$LOGS_DIR"
    
    print_success "All directories created"
    ls -la "$APP_HOME"
}

# ============================================================================
# Step 3: Configure AWS Credentials
# ============================================================================

configure_aws() {
    print_header "Step 3: Configuring AWS Credentials"
    
    if [ -z "$AWS_ACCESS_KEY" ] || [ -z "$AWS_SECRET_KEY" ]; then
        print_info "Using IAM Instance Profile (no credentials needed)"
        print_info "Verifying IAM role..."
        
        if curl -s http://169.254.169.254/latest/meta-data/iam/security-credentials/ &>/dev/null; then
            print_success "IAM Instance Profile detected"
        else
            print_error "No IAM Instance Profile found. Please attach an IAM role to this EC2 instance."
            exit 1
        fi
    else
        print_info "Configuring AWS CLI with provided credentials..."
        aws configure set aws_access_key_id "$AWS_ACCESS_KEY"
        aws configure set aws_secret_access_key "$AWS_SECRET_KEY"
        aws configure set region "$AWS_REGION"
        print_success "AWS credentials configured"
    fi
    
    # Verify AWS connectivity
    if aws sts get-caller-identity &>/dev/null; then
        print_success "AWS connectivity verified"
    else
        print_error "Failed to verify AWS credentials"
        exit 1
    fi
}

# ============================================================================
# Step 4: Create Configuration Files
# ============================================================================

create_configurations() {
    print_header "Step 4: Creating Configuration Files"
    
    # Upload Service Configuration
    cat > "$UPLOAD_SERVICE_DIR/application.properties" << 'EOF'
# Server Configuration
server.port=8080
server.servlet.context-path=/api
spring.application.name=upload-service

# AWS Configuration
aws.region=ap-south-1
aws.s3.bucket-name=book-platform-files-206465504931-ap-south-1-an
aws.s3.injection-folder=injection

# Database Configuration
spring.data.mongodb.uri=mongodb+srv://taskadmin:${MONGODB_PASSWORD}@cluster0.02zhddb.mongodb.net/book_management
spring.data.mongodb.database=book_management

# Logging
logging.level.root=INFO
logging.level.com.bookplatform=DEBUG
logging.file.name=/opt/book-platform/logs/upload-service.log
logging.file.max-size=10MB
logging.file.max-history=10
EOF
    
    # Replace MongoDB password
    sed -i "s/\${MONGODB_PASSWORD}/$MONGODB_PASSWORD/g" "$UPLOAD_SERVICE_DIR/application.properties"
    
    print_success "Upload service configuration created"
    
    # Validation Service Configuration
    cat > "$VALIDATION_SERVICE_DIR/application.yaml" << 'EOF'
server:
  port: 8081
spring:
  application:
    name: validation-service
  data:
    mongodb:
      uri: mongodb+srv://taskadmin:${MONGODB_PASSWORD}@cluster0.02zhddb.mongodb.net/book_management
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
    
    # Replace MongoDB password
    sed -i "s/\${MONGODB_PASSWORD}/$MONGODB_PASSWORD/g" "$VALIDATION_SERVICE_DIR/application.yaml"
    
    print_success "Validation service configuration created"
}

# ============================================================================
# Step 5: Deploy Systemd Services
# ============================================================================

deploy_systemd_services() {
    print_header "Step 5: Deploying Systemd Services"
    
    # Upload Service
    sudo tee /etc/systemd/system/upload-service.service > /dev/null << 'EOF'
[Unit]
Description=Book Platform Upload Service
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/book-platform/upload-service
ExecStart=/usr/bin/java -Xmx1024m -Xms512m -jar upload-service-*.jar
Restart=always
RestartSec=10
StandardOutput=append:/opt/book-platform/logs/upload-service.log
StandardError=append:/opt/book-platform/logs/upload-service-error.log
Environment="AWS_DEFAULT_REGION=ap-south-1"

[Install]
WantedBy=multi-user.target
EOF
    
    print_success "Upload service systemd file created"
    
    # Validation Service
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
    
    print_success "Validation service systemd file created"
    
    # Reload systemd
    sudo systemctl daemon-reload
    
    # Enable services
    sudo systemctl enable upload-service
    sudo systemctl enable validation-service
    
    print_success "Services enabled for auto-start"
}

# ============================================================================
# Step 6: Start Services
# ============================================================================

start_services() {
    print_header "Step 6: Starting Services"
    
    sudo systemctl start upload-service
    sleep 3
    
    sudo systemctl start validation-service
    sleep 3
    
    print_success "Services started"
    
    # Check status
    echo ""
    echo "Upload Service Status:"
    sudo systemctl status upload-service --no-pager
    
    echo ""
    echo "Validation Service Status:"
    sudo systemctl status validation-service --no-pager
}

# ============================================================================
# Step 7: Verify Services
# ============================================================================

verify_services() {
    print_header "Step 7: Verifying Services"
    
    sleep 5
    
    # Check ports
    if netstat -tlnp 2>/dev/null | grep -q ":8080"; then
        print_success "Upload service listening on port 8080"
    else
        print_error "Upload service not listening on port 8080"
    fi
    
    if netstat -tlnp 2>/dev/null | grep -q ":8081"; then
        print_success "Validation service listening on port 8081"
    else
        print_error "Validation service not listening on port 8081"
    fi
    
    # Check logs
    echo ""
    print_info "Recent upload service logs:"
    tail -5 "$LOGS_DIR/upload-service.log" 2>/dev/null || echo "No logs yet"
    
    echo ""
    print_info "Recent validation service logs:"
    tail -5 "$LOGS_DIR/validation-service.log" 2>/dev/null || echo "No logs yet"
}

# ============================================================================
# Main Execution
# ============================================================================

main() {
    print_header "🚀 Book Platform EC2 Deployment"
    print_info "Deployment started at $(date)"
    
    # Check if MongoDB password is set
    if [ -z "$MONGODB_PASSWORD" ]; then
        print_error "MONGODB_PASSWORD environment variable not set"
        echo "Run: export MONGODB_PASSWORD='your-password'"
        exit 1
    fi
    
    verify_prerequisites
    create_directories
    configure_aws
    create_configurations
    deploy_systemd_services
    start_services
    verify_services
    
    print_header "✓ Deployment Complete"
    print_success "Services deployed and running"
    print_info "Deployment completed at $(date)"
    
    echo ""
    echo "Next steps:"
    echo "  1. Monitor logs: tail -f $LOGS_DIR/*.log"
    echo "  2. Test upload service: curl http://localhost:8080/api/health"
    echo "  3. Test validation service: curl http://localhost:8081/health"
    echo "  4. Send test message to SQS queue"
    echo ""
}

# ============================================================================
# Run Main
# ============================================================================

main "$@"
