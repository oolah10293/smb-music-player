// SPDX-License-Identifier: GPL-3.0-or-later
// Keep the isolated Snapclient process tied to the Android service process.
#include <csignal>
#include <sys/prctl.h>
#include <unistd.h>

int snapclient_main(int argc, char** argv);

int main(int argc, char** argv) {
    const auto parent = getppid();
    if (parent == 1 || prctl(PR_SET_PDEATHSIG, SIGTERM) != 0 || getppid() != parent)
        return 1;
    return snapclient_main(argc, argv);
}
