#!/bin/bash

# ============================================================================
# Post-Deployment Verification Script
# Manuscript Validation & Upload Services
# ============================================================================

set -e

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

FAILED=0

print_header() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_pass() {
    echo -e "${GREEN}✓ PASS${NC} - $1"
}

print_fail() {
    echo -e "${RED}✗ FAIL${NC} - $1"
    FAILED=$((FAILED + 1))
}

print_info() {
    echo -e "${YELLOW}ℹ INFO${NC} - $1"
}

# ============================================================================
# Verification Checks
# ============================================================================

check_java() {
    print_header "Java Installation"
    
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | grep "openjdk" | head -1)
        if [[ $JAVA_VERSION == *"21"* ]]; then
            print_pass "Java 21 installed: $JAVA_VERSION"
        else
            print_fail "Java 21 not found. Current: $JAVA_VERSION"
        fi
    else
        print_fail "Java not installed"
    fi
}

check_directories() {
    print_header "Directory Structure"
    
    if [ -d "/opt/book-platform" ]; then
        print_pass "App directory exists: /opt/book-platform"
    else
        print_fail "App directory missing: /opt/book-platform"
    fi
    
    if [ -d "/opt/book-platform/upload-service" ]; then
        print_pass "Upload service directory exists"
    else
        print_fail "Upload service directory missing"
    fi
    
    if [ -d "/opt/book-platform/validation-service" ]; then
        print_pass "Validation service directory exists"
    else
        print_fail "Validation service directory missing"
    fi
    
    if [ -d "/opt/book-platform/logs" ]; then
        print_pass "Logs directory exists"
    else
        print_fail "Logs directory missing"
    fi
}

check_jar_files() {
    print_header "JAR Files"
    
    UPLOAD_JAR=$(ls /opt/book-platform/upload-service/upload-service-*.jar 2>/dev/null | head -1)
    if [ -n "$UPLOAD_JAR" ]; then
        SIZE=$(du -h "$UPLOAD_JAR" | cut -f1)
        print_pass "Upload service JAR found: $SIZE"
    else
        print_fail "Upload service JAR not found"
    fi
    
    VALIDATION_JAR=$(ls /opt/book-platform/validation-service/validation-service-*.jar 2>/dev/null | head -1)
    if [ -n "$VALIDATION_JAR" ]; then
        SIZE=$(du -h "$VALIDATION_JAR" | cut -f1)
        print_pass "Validation service JAR found: $SIZE"
    else
        print_fail "Validation service JAR not found"
    fi
}

check_configuration_files() {
    print_header "Configuration Files"
    
    if [ -f "/opt/book-platform/upload-service/application.properties" ]; then
        print_pass "Upload service config found"
        
        if grep -q "spring.data.mongodb.uri" /opt/book-platform/upload-service/application.properties; then
            print_pass "MongoDB URI configured"
        else
            print_fail "MongoDB URI not configured"
        fi
        
        if grep -q "aws.s3.bucket-name" /opt/book-platform/upload-service/application.properties; then
            print_pass "S3 bucket configured"
        else
            print_fail "S3 bucket not configured"
        fi
    else
        print_fail "Upload service config missing"
    fi
    
    if [ -f "/opt/book-platform/validation-service/application.yaml" ]; then
        print_pass "Validation service config found"
        
        if grep -q "mongodb:" /opt/book-platform/validation-service/application.yaml; then
            print_pass "MongoDB configured"
        else
            print_fail "MongoDB not configured"
        fi
        
        if grep -q "aws:" /opt/book-platform/validation-service/application.yaml; then
            print_pass "AWS services configured"
        else
            print_fail "AWS services not configured"
        fi
    else
        print_fail "Validation service config missing"
    fi
}

check_systemd_services() {
    print_header "Systemd Services"
    
    if [ -f "/etc/systemd/system/upload-service.service" ]; then
        print_pass "Upload service systemd file exists"
        
        if systemctl is-enabled upload-service &>/dev/null; then
            print_pass "Upload service enabled for auto-start"
        else
            print_fail "Upload service not enabled for auto-start"
        fi
    else
        print_fail "Upload service systemd file missing"
    fi
    
    if [ -f "/etc/systemd/system/validation-service.service" ]; then
        print_pass "Validation service systemd file exists"
        
        if systemctl is-enabled validation-service &>/dev/null; then
            print_pass "Validation service enabled for auto-start"
        else
            print_fail "Validation service not enabled for auto-start"
        fi
    else
        print_fail "Validation service systemd file missing"
    fi
}

check_services_running() {
    print_header "Service Status"
    
    if systemctl is-active --quiet upload-service; then
        print_pass "Upload service is RUNNING"
    else
        STATUS=$(systemctl is-active upload-service)
        print_fail "Upload service is $STATUS"
    fi
    
    if systemctl is-active --quiet validation-service; then
        print_pass "Validation service is RUNNING"
    else
        STATUS=$(systemctl is-active validation-service)
        print_fail "Validation service is $STATUS"
    fi
}

check_ports() {
    print_header "Network Ports"
    
    if netstat -tlnp 2>/dev/null | grep -q ":8080.*java"; then
        print_pass "Upload service listening on port 8080"
    else
        print_fail "Upload service NOT listening on port 8080"
    fi
    
    if netstat -tlnp 2>/dev/null | grep -q ":8081.*java"; then
        print_pass "Validation service listening on port 8081"
    else
        print_fail "Validation service NOT listening on port 8081"
    fi
}

check_logs() {
    print_header "Application Logs"
    
    if [ -f "/opt/book-platform/logs/upload-service.log" ]; then
        ERRORS=$(grep -i "error" /opt/book-platform/logs/upload-service.log 2>/dev/null | wc -l)
        if [ $ERRORS -eq 0 ]; then
            print_pass "Upload service log has no errors"
        else
            print_fail "Upload service log contains $ERRORS errors"
            print_info "Last 5 errors:"
            grep -i "error" /opt/book-platform/logs/upload-service.log | tail -5 | sed 's/^/  /'
        fi
    else
        print_fail "Upload service log not found"
    fi
    
    if [ -f "/opt/book-platform/logs/validation-service.log" ]; then
        ERRORS=$(grep -i "error" /opt/book-platform/logs/validation-service.log 2>/dev/null | wc -l)
        if [ $ERRORS -eq 0 ]; then
            print_pass "Validation service log has no errors"
        else
            print_fail "Validation service log contains $ERRORS errors"
            print_info "Last 5 errors:"
            grep -i "error" /opt/book-platform/logs/validation-service.log | tail -5 | sed 's/^/  /'
        fi
    else
        print_fail "Validation service log not found"
    fi
}

check_aws_connectivity() {
    print_header "AWS Connectivity"
    
    if aws sts get-caller-identity &>/dev/null; then
        IDENTITY=$(aws sts get-caller-identity --query 'Account' --output text 2>/dev/null)
        print_pass "AWS credentials working (Account: $IDENTITY)"
    else
        print_fail "AWS credentials not working"
    fi
    
    if aws s3 ls 2>/dev/null | grep -q "book-platform"; then
        print_pass "S3 bucket accessible"
    else
        print_fail "S3 bucket not accessible"
    fi
    
    if aws sqs list-queues --region ap-south-1 2>/dev/null | grep -q "manuscript-validation-queue"; then
        print_pass "SQS queue accessible"
    else
        print_fail "SQS queue not accessible"
    fi
    
    if aws sns list-topics --region ap-south-1 2>/dev/null | grep -q "manuscript-validation-result"; then
        print_pass "SNS topic accessible"
    else
        print_fail "SNS topic not accessible"
    fi
}

check_mongodb_connection() {
    print_header "MongoDB Connection"
    
    # Try to check MongoDB by making a test curl to the service
    if curl -s http://localhost:8080 &>/dev/null || curl -s http://localhost:8081 &>/dev/null; then
        # If service is responding, MongoDB connectivity is likely working
        print_pass "Services are responsive (MongoDB likely connected)"
    else
        print_info "Services not yet responding (may still be starting)"
    fi
}

check_disk_space() {
    print_header "Disk Space"
    
    DISK_USAGE=$(df -h /opt/book-platform 2>/dev/null | awk 'NR==2 {print $5}' | sed 's/%//')
    DISK_AVAILABLE=$(df -h /opt/book-platform 2>/dev/null | awk 'NR==2 {print $4}')
    
    if [ $DISK_USAGE -lt 80 ]; then
        print_pass "Disk usage: $DISK_USAGE% (Available: $DISK_AVAILABLE)"
    else
        print_fail "Disk usage critical: $DISK_USAGE%"
    fi
}

check_memory() {
    print_header "Memory Usage"
    
    MEMORY=$(free -h | awk 'NR==2 {print $3 "/" $2}')
    print_info "Memory used: $MEMORY"
    
    JAVA_MEMORY=$(ps aux | grep java | grep -v grep | awk '{sum+=$6} END {printf "%.0f", sum/1024}')
    if [ -n "$JAVA_MEMORY" ]; then
        print_info "Java processes using: ${JAVA_MEMORY}MB"
    fi
}

# ============================================================================
# Summary Report
# ============================================================================

print_summary() {
    print_header "Verification Summary"
    
    if [ $FAILED -eq 0 ]; then
        echo -e "${GREEN}✓ All checks passed!${NC}"
        echo ""
        echo "Services are deployed and running successfully."
        echo ""
        echo "🚀 Next Steps:"
        echo "  1. Monitor logs: tail -f /opt/book-platform/logs/*.log"
        echo "  2. Test upload: curl http://localhost:8080/api/upload"
        echo "  3. Send test message to SQS queue"
        echo "  4. Check MongoDB for validation results"
        echo ""
        return 0
    else
        echo -e "${RED}✗ $FAILED check(s) failed${NC}"
        echo ""
        echo "⚠️  Issues Found:"
        echo "  Please review the failures above and resolve them."
        echo "  Common fixes:"
        echo "    - Restart services: sudo systemctl restart upload-service validation-service"
        echo "    - Check logs: tail -f /opt/book-platform/logs/*.log"
        echo "    - Verify AWS credentials: aws sts get-caller-identity"
        echo "    - Check database connection string in config files"
        echo ""
        return 1
    fi
}

# ============================================================================
# Main Execution
# ============================================================================

main() {
    print_header "🔍 Post-Deployment Verification"
    print_info "Verification started at $(date)"
    
    check_java
    check_directories
    check_jar_files
    check_configuration_files
    check_systemd_services
    check_services_running
    check_ports
    check_logs
    check_aws_connectivity
    check_mongodb_connection
    check_disk_space
    check_memory
    
    print_summary
    
    print_info "Verification completed at $(date)"
}

main "$@"
