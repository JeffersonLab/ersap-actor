#pragma once

#include <string>
#include <cstddef>
#include <semaphore.h>

class ShmemWriter {
public:
    ShmemWriter(const std::string& shmem_name, size_t size, const std::string& sem_name);
    ~ShmemWriter();

    bool initialize();
    // Write buffer as-is. Caller is responsible for the full layout including any header.
    bool write_data(const void* buffer, size_t size);
    void cleanup();

private:
    std::string shmem_name_;
    std::string sem_name_;
    size_t shmem_size_;
    int fd_;
    void* ptr_;
    sem_t* sem_;
    bool initialized_;
};