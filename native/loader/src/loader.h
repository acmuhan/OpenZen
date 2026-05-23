#pragma once

#include <windows.h>
#include <string>
#include <vector>

namespace loader {

struct JavaProcess {
    DWORD pid;
    std::wstring image_name;
    std::wstring command_line;
    std::wstring window_title;
};

// Enumerate processes whose image is javaw.exe / java.exe.
std::vector<JavaProcess> list_java_processes();

// Inject the given DLL into the target process via CreateRemoteThread +
// LoadLibraryW. Returns an empty string on success or a human-readable error.
std::wstring inject(DWORD pid, const std::wstring& dll_path);

// Extract the IDR_OPENZEN_DLL resource baked into the loader EXE to a
// temp file and return its absolute path. Empty string on failure.
std::wstring extract_embedded_dll();

// Walk top-level windows and return the most informative title belonging to
// the given pid ("" if none found).
std::wstring window_title_for(DWORD pid);

int run_ui(HINSTANCE hInstance);

} // namespace loader
