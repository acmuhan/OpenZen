#include "loader.h"

int APIENTRY wWinMain(HINSTANCE hInstance, HINSTANCE, PWSTR, int) {
    return loader::run_ui(hInstance);
}
