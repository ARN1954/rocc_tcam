#include <stdio.h>
#include <stdint.h>
#include "rocc.h"

// ==== TCAM MMIO Implementation ====

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
        ROCC_INSTRUCTION_DSS(0, result, rs1, rs2, 2); // 0b10: search
    } else { // in_web == 1 && in_csb == 1
        ROCC_INSTRUCTION_DSS(0, result, rs1, rs2, 3); // 0b11
    }

    return (uint32_t)result;
}

void tcam_write(uint32_t address, uint32_t wdata) {
    // Active chip, perform write
    tcam_result = tcam_issue(address, wdata, /*in_web=*/0, /*in_csb=*/0);
}

void write_tcam(uint32_t tcam_addr, uint32_t wdata) {
    tcam_write(tcam_addr, wdata);
}

void search_tcam(uint32_t query) {
    last_query_addr = query;
    // Kick off search (active chip, search mode)
    (void)tcam_issue(query, /*wdata=*/0, /*in_web=*/1, /*in_csb=*/0);
    // Immediately read back status with a second op to observe updated out_pma
    tcam_result = tcam_issue(query, /*wdata=*/0, /*in_web=*/1, /*in_csb=*/0);
}

uint32_t read_tcam_status() {
    // Optionally issue another read of current status to ensure freshness
    tcam_result = tcam_issue(last_query_addr, /*wdata=*/0, /*in_web=*/1, /*in_csb=*/0);
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

    // Display result (after forced readback)
    printf("TCAM match status: 0x%08X\n", read_tcam_status());

    return 0;
}

