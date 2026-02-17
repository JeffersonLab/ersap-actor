#include "haidis_shem_writer.hpp"
#include <iostream>
#include <vector>
#include <cmath>
#include <thread>
#include <chrono>
#include <cstring>
#include <cerrno>
#include <sys/mman.h>
#include <fcntl.h>
#include <unistd.h>

ShmemWriter::ShmemWriter(const std::string& shmem_name, size_t size, const std::string& sem_name)
    : shmem_name_(shmem_name), sem_name_(sem_name), shmem_size_(size), fd_(-1), ptr_(nullptr), sem_(SEM_FAILED), initialized_(false) {}

ShmemWriter::~ShmemWriter() {
    cleanup();
}

bool ShmemWriter::initialize() {
    // Create shared memory object
    fd_ = shm_open(shmem_name_.c_str(), O_CREAT | O_RDWR, 0666);
    if (fd_ == -1) {
        std::cerr << "Failed to create shared memory: " << strerror(errno) << std::endl;
        return false;
    }

    // Set size
    if (ftruncate(fd_, shmem_size_) == -1) {
        std::cerr << "Failed to set shared memory size: " << strerror(errno) << std::endl;
        close(fd_);
        return false;
    }

    // Map to memory
    ptr_ = mmap(nullptr, shmem_size_, PROT_READ | PROT_WRITE, MAP_SHARED, fd_, 0);
    if (ptr_ == MAP_FAILED) {
        std::cerr << "Failed to map shared memory: " << strerror(errno) << std::endl;
        close(fd_);
        return false;
    }

    // Create semaphore (initial value 0 - no data ready)
    sem_ = sem_open(sem_name_.c_str(), O_CREAT, 0666, 0);
    if (sem_ == SEM_FAILED) {
        std::cerr << "Failed to create semaphore: " << strerror(errno) << std::endl;
        munmap(ptr_, shmem_size_);
        close(fd_);
        return false;
    }

    initialized_ = true;
    std::cout << "Shared memory initialized: " << shmem_name_ << " (" << shmem_size_ << " bytes)" << std::endl;
    std::cout << "Semaphore initialized: " << sem_name_ << std::endl;
    return true;
}

bool ShmemWriter::write_data(const void* buffer, size_t size) {
    if (!initialized_) {
        std::cerr << "Shared memory not initialized" << std::endl;
        return false;
    }

    if (size > shmem_size_) {
        std::cerr << "Data too large for shared memory: "
                  << size << " bytes requested, " << shmem_size_ << " bytes available" << std::endl;
        return false;
    }

    // Write buffer as-is — no size header is prepended here.
    // The caller (HaidisLinkActor) owns the full layout: [custom header][double payload].
    std::memcpy(ptr_, buffer, size);

    // Signal that new data is ready
    if (sem_post(sem_) == -1) {
        std::cerr << "Failed to post semaphore: " << strerror(errno) << std::endl;
        return false;
    }

    return true;
}

void ShmemWriter::cleanup() {
    if (ptr_ != nullptr && ptr_ != MAP_FAILED) {
        munmap(ptr_, shmem_size_);
        ptr_ = nullptr;
    }

    if (fd_ != -1) {
        close(fd_);
        fd_ = -1;
    }

    if (sem_ != SEM_FAILED) {
        sem_close(sem_);
        sem_unlink(sem_name_.c_str());
        sem_ = SEM_FAILED;
    }

    if (initialized_) {
        shm_unlink(shmem_name_.c_str());
        initialized_ = false;
    }
}

int main() {
    const std::string shmem_name = std::getenv("SHMEM_NAME") ? std::getenv("SHMEM_NAME") : "/haidis_shmem";
    const size_t shmem_size = std::getenv("SHMEM_SIZE") ? std::stoul(std::getenv("SHMEM_SIZE")) : 10485760;
    const size_t array_size = std::getenv("ARRAY_SIZE") ? std::stoul(std::getenv("ARRAY_SIZE")) : 1000000;
    const std::string sem_name = std::getenv("SEM_NAME") ? std::getenv("SEM_NAME") : "/haidis_sem";

    std::cout << "C++ Source Container Starting..." << std::endl;
    std::cout << "Configuration:" << std::endl;
    std::cout << "  SHMEM_NAME: " << shmem_name << std::endl;
    std::cout << "  SHMEM_SIZE: " << shmem_size << std::endl;
    std::cout << "  ARRAY_SIZE: " << array_size << std::endl;
    std::cout << "  SEM_NAME: " << sem_name << std::endl;

    ShmemWriter writer(shmem_name, shmem_size, sem_name);

    if (!writer.initialize()) {
        std::cerr << "Failed to initialize shared memory writer" << std::endl;
        return 1;
    }

    // Generate and write data continuously
    std::vector<double> data(array_size);
    int iteration = 0;

    // Header layout: [size_t: size_bytes][u32: 2][u32: triplet_count][u32: 3]
    const size_t HEADER_SIZE = sizeof(size_t) + 3 * sizeof(uint32_t);

    while (true) {
        // Generate sample data (sine wave with increasing phase)
        for (size_t i = 0; i < array_size; ++i) {
            data[i] = std::sin(2.0 * M_PI * i / 1000.0 + iteration * 0.1);
        }

        size_t data_bytes = data.size() * sizeof(double);
        uint32_t triplet_count = static_cast<uint32_t>(data.size() / 3);

        // Build [header][payload] buffer
        std::vector<uint8_t> buf(HEADER_SIZE + data_bytes);
        size_t off = 0;
        std::memcpy(buf.data() + off, &data_bytes, sizeof(size_t));   off += sizeof(size_t);
        uint32_t a = 2;
        std::memcpy(buf.data() + off, &a,            sizeof(uint32_t)); off += sizeof(uint32_t);
        std::memcpy(buf.data() + off, &triplet_count, sizeof(uint32_t)); off += sizeof(uint32_t);
        uint32_t b = 3;
        std::memcpy(buf.data() + off, &b,            sizeof(uint32_t)); off += sizeof(uint32_t);
        std::memcpy(buf.data() + off, data.data(),   data_bytes);

        if (writer.write_data(buf.data(), buf.size())) {
            std::cout << "Iteration " << iteration << ": Wrote " << array_size << " doubles to shared memory" << std::endl;
        } else {
            std::cerr << "Failed to write data" << std::endl;
        }

        iteration++;
        std::this_thread::sleep_for(std::chrono::seconds(2));
    }

    return 0;
}