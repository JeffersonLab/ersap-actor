cmake_minimum_required(VERSION 3.10)
project(ersap-actor-cpp VERSION 1.0.0)

# Set C++ standard
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# If ERSAP_HOME is defined, set default install path and prefix path
if(DEFINED ENV{ERSAP_HOME} AND CMAKE_INSTALL_PREFIX_INITIALIZED_TO_DEFAULT)
  set(CMAKE_INSTALL_PREFIX "$ENV{ERSAP_HOME}" CACHE PATH "" FORCE)
  list(APPEND CMAKE_PREFIX_PATH "$ENV{ERSAP_HOME}")
endif()

# Set plugin install directory if not set
if(NOT DEFINED CMAKE_INSTALL_PLUGINDIR)
  set(CMAKE_INSTALL_PLUGINDIR plugins CACHE PATH "Ersap plugins directory (plugins)")
endif()
mark_as_advanced(CMAKE_INSTALL_PLUGINDIR)

# Find required packages
find_package(PkgConfig REQUIRED)

# Find protobuf compatible with xMsg library
# Use pkg-config to find protobuf version that matches system
pkg_check_modules(PROTOBUF REQUIRED protobuf>=3.0)
message(STATUS "Found protobuf version: ${PROTOBUF_VERSION}")
message(STATUS "Protobuf include dirs: ${PROTOBUF_INCLUDE_DIRS}")
message(STATUS "Protobuf libraries: ${PROTOBUF_LIBRARIES}")

# Use the same protobuf version for compilation
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${PROTOBUF_CFLAGS_OTHER}")
link_directories(${PROTOBUF_LIBRARY_DIRS})

# Find ERSAP C++ library
set(ERSAP_ROOT $ENV{ERSAP_HOME})
find_path(ERSAP_INCLUDE_DIR ersap/engine.hpp HINTS ${ERSAP_ROOT}/include)
find_library(ERSAP_LIBRARY ersap HINTS ${ERSAP_ROOT}/lib)

if(NOT ERSAP_INCLUDE_DIR OR NOT ERSAP_LIBRARY)
    message(FATAL_ERROR "ERSAP C++ library not found. Please install ersap-cpp or set ERSAP_HOME.")
endif()

# Find xMsg C++ library for protobuf serialization from ERSAP installation
find_library(XMSG_LIBRARY xmsg HINTS ${ERSAP_ROOT}/lib)
find_path(XMSG_INCLUDE_DIR xmsg/proto/data.h HINTS ${ERSAP_ROOT}/include)

if(NOT XMSG_LIBRARY OR NOT XMSG_INCLUDE_DIR)
    message(FATAL_ERROR "xMsg C++ library not found in ERSAP installation. Please ensure xmsg is installed in ${ERSAP_ROOT}")
endif()

message(STATUS "Found xMsg library: ${XMSG_LIBRARY}")
message(STATUS "Found xMsg include dir: ${XMSG_INCLUDE_DIR}")

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${ERSAP_INCLUDE_DIR}
    ${XMSG_INCLUDE_DIR}
    ${PROTOBUF_INCLUDE_DIRS}
)

# Source files for individual libraries
set(DATATYPE_SOURCES
    src/CodaTimeFrameDataType.cpp
    src/CodaTimeFrameBinaryDataType.cpp
)

set(DATATYPE_HEADERS
    include/CodaTimeFrameDataType.hpp
    include/CodaTimeFrameBinaryDataType.hpp
)

set(ACTOR_SOURCES
    src/CodaTimeFramePrinterActor.cpp
)

set(ACTOR_HEADERS
    include/CodaTimeFramePrinterActor.hpp
)

set(BINARY_ACTOR_SOURCES
    src/CodaTimeFrameBinaryPrinterActor.cpp
)

set(BINARY_ACTOR_HEADERS
    include/CodaTimeFrameBinaryPrinterActor.hpp
)

# Create shared library for the data type
add_library(CodaTimeFrameDataType SHARED ${DATATYPE_SOURCES} ${DATATYPE_HEADERS})

# Link libraries for data type
target_link_libraries(CodaTimeFrameDataType
    ${ERSAP_LIBRARY}
    ${XMSG_LIBRARY}
    ${PROTOBUF_LIBRARIES}
)

# Set library properties for data type
set_target_properties(CodaTimeFrameDataType PROPERTIES
    VERSION ${PROJECT_VERSION}
    SOVERSION 1
    PUBLIC_HEADER "${DATATYPE_HEADERS}"
)

# Create shared library for the ERSAP actor
add_library(CodaTimeFramePrinterActor SHARED ${ACTOR_SOURCES} ${ACTOR_HEADERS})

# Link libraries for actor (depends on data type library)
target_link_libraries(CodaTimeFramePrinterActor
    ${ERSAP_LIBRARY}
    CodaTimeFrameDataType
)

# Set library properties for actor
set_target_properties(CodaTimeFramePrinterActor PROPERTIES
    VERSION ${PROJECT_VERSION}
    SOVERSION 1
    PUBLIC_HEADER "${ACTOR_HEADERS}"
)

# Create shared library for the binary printer ERSAP actor
add_library(CodaTimeFrameBinaryPrinterActor SHARED ${BINARY_ACTOR_SOURCES} ${BINARY_ACTOR_HEADERS})

# Link libraries for binary printer actor (depends on data type library)
target_link_libraries(CodaTimeFrameBinaryPrinterActor
    ${ERSAP_LIBRARY}
    CodaTimeFrameDataType
)

# Set library properties for binary printer actor
set_target_properties(CodaTimeFrameBinaryPrinterActor PROPERTIES
    VERSION ${PROJECT_VERSION}
    SOVERSION 1
    PUBLIC_HEADER "${BINARY_ACTOR_HEADERS}"
)

# Installation rules for all libraries
install(TARGETS CodaTimeFrameDataType CodaTimeFramePrinterActor CodaTimeFrameBinaryPrinterActor
    LIBRARY DESTINATION lib
    PUBLIC_HEADER DESTINATION include/ersap-actor-cpp
)

# Optional: Create a simple test executable
option(BUILD_TESTS "Build test executables" OFF)
if(BUILD_TESTS)
    add_executable(test-ev-event-serializer tests/test_coda_timeframe_serializer.cpp)
    target_link_libraries(test-ev-event-serializer ${ERSAP_LIBRARY} CodaTimeFrameDataType)
endif()

# Print configuration summary
message(STATUS "=====================================")
message(STATUS "ERSAP Actor C++ Configuration Summary")
message(STATUS "=====================================")
message(STATUS "Version: ${PROJECT_VERSION}")
message(STATUS "C++ Standard: ${CMAKE_CXX_STANDARD}")
message(STATUS "ERSAP Include Dir: ${ERSAP_INCLUDE_DIR}")
message(STATUS "ERSAP Library: ${ERSAP_LIBRARY}")
message(STATUS "Build Type: ${CMAKE_BUILD_TYPE}")
message(STATUS "Install Prefix: ${CMAKE_INSTALL_PREFIX}")
message(STATUS "Plugin Install Dir: ${CMAKE_INSTALL_PLUGINDIR}")
message(STATUS "=====================================")

# Usage instructions
message(STATUS "")
message(STATUS "Build Instructions:")
message(STATUS "  mkdir build && cd build")
message(STATUS "  cmake ..")
message(STATUS "  make")
message(STATUS "  make install")
message(STATUS "")
message(STATUS "Usage in ERSAP:")
message(STATUS "  Set LD_LIBRARY_PATH to include the installation directory")
message(STATUS "  Configure ERSAP to use 'CodaTimeFramePrinterActor' as the engine library")
message(STATUS "  Data type library 'CodaTimeFrameDataType' will be loaded automatically")
