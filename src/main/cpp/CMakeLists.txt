cmake_minimum_required(VERSION 3.10)
project(ersap-actor-cpp VERSION 1.0.0)

# Set C++ standard (fallback for older CMake or external dependencies)
set(CMAKE_CXX_STANDARD 17)
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

# Add protobuf library directories
if(PROTOBUF_LIBRARY_DIRS)
    link_directories(${PROTOBUF_LIBRARY_DIRS})
endif()

# Find ERSAP C++ library
set(ERSAP_ROOT $ENV{ERSAP_HOME})
find_path(ERSAP_INCLUDE_DIR ersap/engine.hpp HINTS ${ERSAP_ROOT}/include)
find_library(ERSAP_LIBRARY ersap HINTS ${ERSAP_ROOT}/lib)

if(NOT ERSAP_INCLUDE_DIR OR NOT ERSAP_LIBRARY)
    message(FATAL_ERROR "ERSAP C++ library not found. Please install ersap-cpp or set ERSAP_HOME.")
endif()

# Find ET library (optional, for HaidisActor)
find_library(ET_LIBRARY et HINTS $ENV{CODA}/Linux-x86_64/lib $ENV{CODA}/lib $ENV{CODA})
find_path(ET_INCLUDE_DIR et.h HINTS $ENV{CODA}/common/include $ENV{CODA}/include $ENV{CODA})

if(ET_LIBRARY AND ET_INCLUDE_DIR)
    message(STATUS "Found ET library: ${ET_LIBRARY}")
    message(STATUS "Found ET include dir: ${ET_INCLUDE_DIR}")
    set(BUILD_HAIDIS_ACTOR ON)
else()
    message(STATUS "ET library not found. HaidisActor will not be built.")
    message(STATUS "To build HaidisActor, set CODA environment variable to ET installation path.")
    set(BUILD_HAIDIS_ACTOR OFF)
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

# Add ET include directory if available
if(BUILD_HAIDIS_ACTOR)
    include_directories(${ET_INCLUDE_DIR})
endif()

# Source files for individual libraries
set(DATATYPE_SOURCES
    src/CodaTimeFrameDataType.cpp
)

set(DATATYPE_HEADERS
    include/CodaTimeFrameDataType.hpp
)

set(BINARY_DATATYPE_SOURCES
    src/CodaTImeFrameBinaryDataType.cpp
)

set(BINARY_DATATYPE_HEADERS
    include/CodaTImeFrameBinaryDataType.hpp
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

set(HAIDIS_ACTOR_SOURCES
    src/haidis_actor.cpp
)

set(HAIDIS_ACTOR_HEADERS
    include/HaidisActor.hpp
)

# Create shared library for the data type
add_library(CodaTimeFrameDataType SHARED ${DATATYPE_SOURCES} ${DATATYPE_HEADERS})

# Require C++17 for this target
target_compile_features(CodaTimeFrameDataType PUBLIC cxx_std_17)

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

# Create shared library for the binary data type
add_library(CodaTimeFrameBinaryDataType SHARED ${BINARY_DATATYPE_SOURCES} ${BINARY_DATATYPE_HEADERS})

# Require C++17 for this target
target_compile_features(CodaTimeFrameBinaryDataType PUBLIC cxx_std_17)

# Link libraries for binary data type
target_link_libraries(CodaTimeFrameBinaryDataType
    ${ERSAP_LIBRARY}
)

# Set library properties for binary data type
set_target_properties(CodaTimeFrameBinaryDataType PROPERTIES
    VERSION ${PROJECT_VERSION}
    SOVERSION 1
    PUBLIC_HEADER "${BINARY_DATATYPE_HEADERS}"
)

# Create shared library for the ERSAP actor
add_library(CodaTimeFramePrinterActor SHARED ${ACTOR_SOURCES} ${ACTOR_HEADERS})

# Require C++17 for this target
target_compile_features(CodaTimeFramePrinterActor PUBLIC cxx_std_17)

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

# Require C++17 for this target
target_compile_features(CodaTimeFrameBinaryPrinterActor PUBLIC cxx_std_17)

# Link libraries for binary printer actor (depends on binary data type library)
target_link_libraries(CodaTimeFrameBinaryPrinterActor
    ${ERSAP_LIBRARY}
    CodaTimeFrameBinaryDataType
)

# Set library properties for binary printer actor
set_target_properties(CodaTimeFrameBinaryPrinterActor PROPERTIES
    VERSION ${PROJECT_VERSION}
    SOVERSION 1
    PUBLIC_HEADER "${BINARY_ACTOR_HEADERS}"
)

# Create shared library for the HaidisActor (if ET library is available)
if(BUILD_HAIDIS_ACTOR)
    add_library(HaidisActor SHARED ${HAIDIS_ACTOR_SOURCES} ${HAIDIS_ACTOR_HEADERS})

    # Require C++17 for this target
    target_compile_features(HaidisActor PUBLIC cxx_std_17)

    # Link libraries for HaidisActor
    target_link_libraries(HaidisActor
        ${ERSAP_LIBRARY}
        ${ET_LIBRARY}
    )

    # Set library properties for HaidisActor
    set_target_properties(HaidisActor PROPERTIES
        VERSION ${PROJECT_VERSION}
        SOVERSION 1
        PUBLIC_HEADER "${HAIDIS_ACTOR_HEADERS}"
    )
endif()

# Installation rules for all libraries
if(BUILD_HAIDIS_ACTOR)
    install(TARGETS CodaTimeFrameDataType CodaTimeFrameBinaryDataType CodaTimeFramePrinterActor CodaTimeFrameBinaryPrinterActor HaidisActor
        LIBRARY DESTINATION lib
        PUBLIC_HEADER DESTINATION include/ersap-actor-cpp
    )
else()
    install(TARGETS CodaTimeFrameDataType CodaTimeFrameBinaryDataType CodaTimeFramePrinterActor CodaTimeFrameBinaryPrinterActor
        LIBRARY DESTINATION lib
        PUBLIC_HEADER DESTINATION include/ersap-actor-cpp
    )
endif()

# Optional: Create a simple test executable
option(BUILD_TESTS "Build test executables" OFF)
if(BUILD_TESTS)
    add_executable(test-ev-event-serializer tests/test_coda_timeframe_serializer.cpp)
    target_compile_features(test-ev-event-serializer PRIVATE cxx_std_17)
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
