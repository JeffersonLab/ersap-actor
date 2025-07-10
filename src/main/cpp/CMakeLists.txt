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

# Find ERSAP C++ library
set(ERSAP_ROOT $ENV{ERSAP_HOME})
find_path(ERSAP_INCLUDE_DIR ersap/engine.hpp HINTS ${ERSAP_ROOT}/include)
find_library(ERSAP_LIBRARY ersap HINTS ${ERSAP_ROOT}/lib)

if(NOT ERSAP_INCLUDE_DIR OR NOT ERSAP_LIBRARY)
    message(FATAL_ERROR "ERSAP C++ library not found. Please install ersap-cpp or set ERSAP_HOME.")
endif()

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${ERSAP_INCLUDE_DIR}
)

# Source files
set(SOURCES
    src/CodaTimeFrameDataType.cpp
    src/CodaTimeFramePrinterActor.cpp
)

set(HEADERS
    include/CodaTimeFrameDataType.hpp
    include/CodaTimeFramePrinterActor.hpp
)

# Create shared library for the ERSAP actor
add_library(ersap-actor-cpp SHARED ${SOURCES} ${HEADERS})

# Link libraries
target_link_libraries(ersap-actor-cpp
    ${ERSAP_LIBRARY}
)

# Set library properties
set_target_properties(ersap-actor-cpp PROPERTIES
    VERSION ${PROJECT_VERSION}
    SOVERSION 1
    PUBLIC_HEADER "${HEADERS}"
)

# Installation rules
install(TARGETS ersap-actor-cpp
    LIBRARY DESTINATION lib
    PUBLIC_HEADER DESTINATION include/ersap-actor-cpp
)

# Optional: Create a simple test executable
option(BUILD_TESTS "Build test executables" OFF)
if(BUILD_TESTS)
    add_executable(test-ev-event-serializer tests/test_coda_timeframe_serializer.cpp src/CodaTimeFrameDataType.cpp)
    target_link_libraries(test-ev-event-serializer ${ERSAP_LIBRARY})
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
message(STATUS "  Configure ERSAP to use 'ersap-actor-cpp' as the engine library")
