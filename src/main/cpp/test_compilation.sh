#!/bin/bash

# Quick C++ Compilation Test Script
# Tests if the CodaTimeFramePrinterActor compiles successfully

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() {
    echo -e "${BLUE}[$(date '+%H:%M:%S')] $1${NC}"
}

error() {
    echo -e "${RED}[ERROR] $1${NC}" >&2
}

success() {
    echo -e "${GREEN}[SUCCESS] $1${NC}"
}

warn() {
    echo -e "${YELLOW}[WARNING] $1${NC}"
}

# Test compilation
test_compilation() {
    log "Testing C++ compilation..."
    
    cd "$SCRIPT_DIR"
    
    # Clean previous build
    if [[ -d "$BUILD_DIR" ]]; then
        rm -rf "$BUILD_DIR"
    fi
    
    mkdir -p "$BUILD_DIR"
    cd "$BUILD_DIR"
    
    # Configure
    log "Running CMake configuration..."
    if cmake .. 2>&1 | tee cmake.log; then
        success "CMake configuration successful"
    else
        error "CMake configuration failed"
        cat cmake.log
        exit 1
    fi
    
    # Build
    log "Building C++ components..."
    if make -j$(nproc) 2>&1 | tee make.log; then
        success "C++ compilation successful"
    else
        error "C++ compilation failed"
        cat make.log
        exit 1
    fi
    
    # Check if library was created
    if [[ -f "libersap-actor-cpp.so" ]]; then
        success "C++ library created: libersap-actor-cpp.so"
        
        # Show library info
        log "Library information:"
        file libersap-actor-cpp.so
        ldd libersap-actor-cpp.so 2>/dev/null || echo "ldd not available"
        
        # Test if symbols are properly exported
        if command -v nm &> /dev/null; then
            log "Checking exported symbols..."
            nm -D libersap-actor-cpp.so | grep -E "(create_engine|CodaTimeFramePrinterActor)" || warn "Expected symbols not found"
        fi
    else
        error "C++ library not created"
        exit 1
    fi
    
    # Test installation
    log "Testing installation..."
    if make install 2>&1 | tee install.log; then
        success "Installation successful"
    else
        warn "Installation failed (may not be critical)"
        cat install.log
    fi
    
    success "All C++ compilation tests passed"
}

# Validate environment
validate_environment() {
    log "Validating compilation environment..."
    
    # Check for required tools
    local required_tools=("cmake" "make" "g++")
    for tool in "${required_tools[@]}"; do
        if ! command -v "$tool" &> /dev/null; then
            error "Required tool not found: $tool"
            exit 1
        fi
    done
    
    # Check C++ standard
    local cpp_version=$(g++ --version | head -n1)
    log "C++ compiler: $cpp_version"
    
    # Check CMake version
    local cmake_version=$(cmake --version | head -n1)
    log "CMake version: $cmake_version"
    
    # Check ERSAP environment
    if [[ -z "$ERSAP_HOME" ]]; then
        warn "ERSAP_HOME not set, using default paths"
    else
        log "ERSAP_HOME: $ERSAP_HOME"
    fi
    
    success "Environment validation complete"
}

# Main execution
main() {
    log "Starting C++ compilation test"
    
    validate_environment
    test_compilation
    
    success "C++ compilation test completed successfully"
    
    log "Next steps:"
    log "1. Set LD_LIBRARY_PATH to include: $BUILD_DIR"
    log "2. Run the full pipeline with: ./run_coda_pipeline.sh"
    log "3. Test with mock data: ./mock_data_generator.py --stream"
}

# Handle cleanup on exit
cleanup() {
    if [[ -d "$BUILD_DIR" ]]; then
        log "Build artifacts available in: $BUILD_DIR"
    fi
}

trap cleanup EXIT

# Run main function
main "$@"