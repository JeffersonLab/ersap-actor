# Build Instructions for HaidisActor

This guide explains how to build and install the HaidisActor C++ ERSAP service.

## Quick Start (Complete Build and Install)

```bash
# 1. Set environment variables
export ERSAP_HOME=/path/to/your/ersap/installation
export CODA=/path/to/coda/et/installation

# 2. Navigate to C++ source directory
cd /Users/gurjyan/Documents/Devel/ersap-actor/src/main/cpp

# 3. Create build directory
mkdir -p build && cd build

# 4. Configure, build, and install to $ERSAP_HOME
cmake ..
make -j$(nproc)
make install

# 5. Verify installation
ls -lh $ERSAP_HOME/lib/libHaidisActor.so*
```

After installation, the service will be available at `$ERSAP_HOME/lib/libHaidisActor.so` and can be used in ERSAP orchestrations.

---

## Prerequisites

### Required Environment Variables

1. **ERSAP_HOME** - Path to ERSAP C++ installation
   ```bash
   export ERSAP_HOME=/path/to/ersap-cpp
   ```

2. **CODA** - Path to CODA ET library installation
   ```bash
   export CODA=/path/to/coda/et
   ```

### Required Dependencies

- **CMake** >= 3.10
- **C++ compiler** with C++14 support (g++, clang++)
- **ERSAP C++ library** (ersap-cpp) installed in `$ERSAP_HOME`
- **xMsg C++ library** (should be in `$ERSAP_HOME/lib`)
- **Protocol Buffers** >= 3.0 (protobuf)
- **CODA ET library** (et) for event transport

## Build Steps

### 1. Navigate to the C++ source directory
```bash
cd /Users/gurjyan/Documents/Devel/ersap-actor/src/main/cpp
```

### 2. Create a build directory
```bash
mkdir -p build
cd build
```

### 3. Configure with CMake
```bash
cmake ..
```

**Expected output:**
- CMake should find protobuf, ERSAP libraries, xMsg, and ET library
- You should see: `Found ET library: /path/to/libcoda_et.so`
- Status message: `BUILD_HAIDIS_ACTOR ON`

**If ET library is not found:**
```bash
# Make sure CODA is set correctly
export CODA=/usr/local/coda    # or your ET installation path
cmake ..
```

### 4. Build the project
```bash
make
```

**Output:** This builds several shared libraries:
- `libCodaTimeFrameDataType.so` - Data type library
- `libCodaTimeFramePrinterActor.so` - Printer actor
- `libCodaTimeFrameBinaryPrinterActor.so` - Binary printer
- **`libHaidisActor.so`** - Your HaidisActor service (main target)

### 5. Install to $ERSAP_HOME

**IMPORTANT:** The installation step is required for ERSAP to find and load the HaidisActor service.

```bash
make install
```

**What gets installed:**

Libraries are installed to `$ERSAP_HOME/lib/`:
- `libHaidisActor.so` (or `.dylib` on macOS) - Main HaidisActor service
- `libCodaTimeFrameDataType.so` - Data type library
- `libCodaTimeFramePrinterActor.so` - Printer actor
- `libCodaTimeFrameBinaryPrinterActor.so` - Binary printer

Headers are installed to `$ERSAP_HOME/include/ersap-actor-cpp/`:
- `HaidisActor.hpp`
- `CodaTimeFrameDataType.hpp`
- Other header files

**Verify Installation:**
```bash
ls -lh $ERSAP_HOME/lib/libHaidisActor.so*
ls -lh $ERSAP_HOME/include/ersap-actor-cpp/
```

**Custom Install Location (Optional):**

If you want to install to a different location:
```bash
cmake -DCMAKE_INSTALL_PREFIX=/custom/path ..
make install
```

But for ERSAP to automatically find the service, installing to `$ERSAP_HOME` is recommended.

## Quick Build Script

Alternatively, use the provided test script:
```bash
cd /Users/gurjyan/Documents/Devel/ersap-actor/src/main/cpp
./test_compilation.sh
```

## Verify Build

Check that HaidisActor was built:
```bash
ls -lh build/libHaidisActor.so*
```

Check symbols in the library:
```bash
nm -D build/libHaidisActor.so | grep create_engine
```

You should see the exported `create_engine` function that ERSAP uses to load the service.

## Common Build Issues

### Issue: "ERSAP C++ library not found"
**Solution:**
```bash
export ERSAP_HOME=/path/to/your/ersap-installation
cmake ..
```

### Issue: "ET library not found. HaidisActor will not be built."
**Solution:**
```bash
export CODA=/path/to/coda/et/installation
cmake ..
```

### Issue: "protobuf >= 3.0 not found"
**Solution:** Install protobuf via package manager:
```bash
# macOS
brew install protobuf

# Linux (Debian/Ubuntu)
sudo apt-get install libprotobuf-dev protobuf-compiler

# Linux (RHEL/CentOS)
sudo yum install protobuf-devel
```

### Issue: Linking errors with xMsg
**Solution:** Ensure xMsg is installed in `$ERSAP_HOME`:
```bash
ls $ERSAP_HOME/lib/libxmsg*
ls $ERSAP_HOME/include/xmsg/
```

## Post-Installation: Using HaidisActor in ERSAP

After successfully building and installing to `$ERSAP_HOME`, you can use HaidisActor in ERSAP:

1. **Ensure ERSAP environment is configured:**
   ```bash
   # Add ERSAP libraries to library path
   export LD_LIBRARY_PATH=$ERSAP_HOME/lib:$LD_LIBRARY_PATH  # Linux
   export DYLD_LIBRARY_PATH=$ERSAP_HOME/lib:$DYLD_LIBRARY_PATH  # macOS

   # Verify HaidisActor is installed
   ls $ERSAP_HOME/lib/libHaidisActor.so*
   ```

2. **Configure services.yaml:**
   ```yaml
   services:
     - class: HaidisActor
       name: haidis
       lang: cpp
   ```

3. **Run with ERSAP orchestrator:**
   ```bash
   ersap-orchestrator services.yaml
   ```

## Development Build Tips

### Debug Build
```bash
cmake -DCMAKE_BUILD_TYPE=Debug ..
make
```

### Verbose Build (see compile commands)
```bash
make VERBOSE=1
```

### Clean Build
```bash
make clean
# Or completely rebuild:
rm -rf build
mkdir build
cd build
cmake ..
make
```

### Build Only HaidisActor
```bash
make HaidisActor
```

## File Locations

- **Source:** `src/haidis_actor.cpp`
- **Header:** `include/HaidisActor.hpp`
- **Built library:** `build/libHaidisActor.so` (or `.dylib` on macOS)
- **Installed library:** `$ERSAP_HOME/lib/libHaidisActor.so`

## Configuration Summary

From CMakeLists.txt, HaidisActor requires:
- C++14 standard
- ERSAP library (`libersap`)
- ET library (`libcoda_et` or `libet`)
- Linked as a shared library with SOVERSION 1

The service outputs an array of doubles (`binary/array-double` MIME type) containing 16 physics four-vector values from ET system events.
