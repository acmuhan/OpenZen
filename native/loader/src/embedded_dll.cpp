#include "loader.h"
#include "resource.h"

namespace loader {

std::wstring extract_embedded_dll() {
    HMODULE self = GetModuleHandleW(nullptr);
    HRSRC info = FindResourceW(self, MAKEINTRESOURCEW(IDR_OPENZEN_DLL), RT_RCDATA);
    if (!info) return L"";
    DWORD size = SizeofResource(self, info);
    if (size == 0) return L"";
    HGLOBAL loaded = LoadResource(self, info);
    if (!loaded) return L"";
    void* data = LockResource(loaded);
    if (!data) return L"";

    wchar_t tmp[MAX_PATH];
    if (GetTempPathW(MAX_PATH, tmp) == 0) return L"";

    wchar_t dir[MAX_PATH];
    std::swprintf(dir, MAX_PATH, L"%sOpenZenLoader", tmp);
    CreateDirectoryW(dir, nullptr);

    wchar_t path[MAX_PATH];
    std::swprintf(path, MAX_PATH, L"%s\\OpenZen.dll", dir);

    HANDLE file = CreateFileW(path, GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS,
                              FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) return L"";

    DWORD written = 0;
    BOOL ok = WriteFile(file, data, size, &written, nullptr);
    CloseHandle(file);
    if (!ok || written != size) return L"";

    return std::wstring(path);
}

} // namespace loader
