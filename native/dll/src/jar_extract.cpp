#include "openzen.h"
#include "resource.h"

namespace openzen::jar {

bool extract_embedded(std::wstring& out_path) {
    HRSRC info = FindResourceW(g_self_module, MAKEINTRESOURCEW(IDR_ZEN_JAR), RT_RCDATA);
    if (!info) {
        log::error("FindResource(IDR_ZEN_JAR) failed: %lu", GetLastError());
        return false;
    }
    DWORD size = SizeofResource(g_self_module, info);
    if (size == 0) {
        log::error("Embedded zen.jar resource is empty");
        return false;
    }
    HGLOBAL loaded = LoadResource(g_self_module, info);
    if (!loaded) {
        log::error("LoadResource failed: %lu", GetLastError());
        return false;
    }
    void* data = LockResource(loaded);
    if (!data) {
        log::error("LockResource failed");
        return false;
    }

    wchar_t tmp[MAX_PATH];
    if (GetTempPathW(MAX_PATH, tmp) == 0) {
        log::error("GetTempPath failed: %lu", GetLastError());
        return false;
    }

    wchar_t path[MAX_PATH];
    std::swprintf(path, MAX_PATH, L"%sopenzen-%lu.jar", tmp, GetCurrentProcessId());

    HANDLE file = CreateFileW(path, GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS,
                               FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) {
        log::error("CreateFile %ls failed: %lu", path, GetLastError());
        return false;
    }

    DWORD written = 0;
    BOOL ok = WriteFile(file, data, size, &written, nullptr);
    CloseHandle(file);
    if (!ok || written != size) {
        log::error("WriteFile %ls failed: %lu", path, GetLastError());
        return false;
    }

    out_path.assign(path);
    log::info("Extracted zen.jar (%lu bytes) to %ls", (unsigned long)size, path);
    return true;
}

} // namespace openzen::jar
