#pragma once
// Win32 file-backed shared memory wrapper.
//
// The Java side maps the same file with FileChannel.map; Windows keeps all
// views of a given file page-coherent across processes, so no flush is needed
// for IPC visibility.

#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>

#include <cstdint>
#include <string>

namespace stella {

class SharedFile {
public:
    ~SharedFile() { close(); }

    bool open(const std::wstring& path, uint64_t size) {
        file_ = ::CreateFileW(path.c_str(),
                              GENERIC_READ | GENERIC_WRITE,
                              FILE_SHARE_READ | FILE_SHARE_WRITE,
                              nullptr,
                              OPEN_ALWAYS,
                              FILE_ATTRIBUTE_NORMAL,
                              nullptr);
        if (file_ == INVALID_HANDLE_VALUE) {
            return false;
        }

        LARGE_INTEGER li{};
        li.QuadPart = static_cast<LONGLONG>(size);
        if (!::SetFilePointerEx(file_, li, nullptr, FILE_BEGIN) || !::SetEndOfFile(file_)) {
            close();
            return false;
        }

        mapping_ = ::CreateFileMappingW(file_, nullptr, PAGE_READWRITE, 0, 0, nullptr);
        if (!mapping_) {
            close();
            return false;
        }

        view_ = static_cast<uint8_t*>(::MapViewOfFile(mapping_, FILE_MAP_ALL_ACCESS, 0, 0, 0));
        return view_ != nullptr;
    }

    void close() {
        if (view_) {
            ::UnmapViewOfFile(view_);
            view_ = nullptr;
        }
        if (mapping_) {
            ::CloseHandle(mapping_);
            mapping_ = nullptr;
        }
        if (file_ != INVALID_HANDLE_VALUE) {
            ::CloseHandle(file_);
            file_ = INVALID_HANDLE_VALUE;
        }
    }

    uint8_t* data() { return view_; }
    const uint8_t* data() const { return view_; }

private:
    HANDLE file_ = INVALID_HANDLE_VALUE;
    HANDLE mapping_ = nullptr;
    uint8_t* view_ = nullptr;
};

} // namespace stella
