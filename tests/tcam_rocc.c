#include <stdio.h>
#include <stdint.h>
#include "rocc.h"

// ==== TCAM RoCC Helpers ====

static uint32_t tcam_result = 0;
static uint32_t last_query_addr = 0;

static inline uint32_t tcam_issue(uint32_t address, uint32_t wdata, uint8_t in_web, uint8_t in_csb) {
    uint32_t wmask = 0xF; // write all bits

    uint64_t rs1 = ((uint64_t)(wmask & 0xF) << 28) | (address & 0x0FFFFFFF);
    uint64_t rs2 = (uint64_t)wdata;
    uint64_t result = 0;

    // funct encoding: Cat(in_web, in_csb)
    if (in_web == 0 && in_csb == 0) {
        ROCC_INSTRUCTION_DSS(0, result, rs1, rs2, 0); // 0b00: write
    } else if (in_web == 0 && in_csb == 1) {
        ROCC_INSTRUCTION_DSS(0, result, rs1, rs2, 1); // 0b01
    } else if (in_web == 1 && in_csb == 0) {
        ROCC_INSTRUCTION_DSS(0, result, rs1, rs2, 2); // 0b10: search/read
    } else { // in_web == 1 && in_csb == 1
        ROCC_INSTRUCTION_DSS(0, result, rs1, rs2, 3); // 0b11
    }

    return (uint32_t)result;
}

static inline void tcam_write(uint32_t address, uint32_t wdata) {
    // Active chip, perform write; handshake ensures completion
    tcam_result = tcam_issue(address, wdata, /*in_web=*/0, /*in_csb=*/0);
}

static inline uint32_t tcam_read_status(uint32_t address) {
    // Active chip, read/search path; returns current status
    return tcam_issue(address, /*wdata=*/0, /*in_web=*/1, /*in_csb=*/0);
}

void write_tcam(uint32_t tcam_addr, uint32_t wdata) {
    tcam_write(tcam_addr, wdata);
}

void search_tcam(uint32_t query) {
    last_query_addr = query;

    // Kick off search
    (void)tcam_issue(query, /*wdata=*/0, /*in_web=*/1, /*in_csb=*/0);

    // Poll status to allow all TCAM banks to complete and the reduction to settle
    // Break early if a non-zero match status is observed; otherwise, take the last value
    const int max_polls = 64; // allow enough cycles for multi-bank updates
    uint32_t status = 0;
    for (int i = 0; i < max_polls; i++) {
        uint32_t cur = tcam_read_status(query);
        status = cur; // keep latest
        if (cur != 0) break;
    }
    tcam_result = status;
}

uint32_t read_tcam_status() {
    // Ensure freshness by reading again and returning the latest
    tcam_result = tcam_read_status(last_query_addr);
    return tcam_result;
}

// ==== Test Application ====

int main() {
    printf("=== TCAM RoCC Test ===\n");

    // Populate TCAM with example values
    write_tcam(0x00000005, 0x00000010);
    write_tcam(0x00000085, 0x00000000);
    write_tcam(0x00000105, 0x00000010);
    write_tcam(0x00000185, 0x00000000);
    write_tcam(0x00000205, 0x00000010);
    write_tcam(0x00000285, 0x00000000);
    write_tcam(0x00000305, 0x00000010);
    write_tcam(0x00000385, 0x00000000);

    // Search for a value in TCAM
    uint32_t search_query = 0x00A14285;
    search_tcam(search_query);

    // Display result after polling-based settle
    printf("TCAM match status: 0x%08X\n", read_tcam_status());

    return 0;
}

