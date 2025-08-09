#include <stdio.h>
#include <stdint.h>
#include "rocc.h"

// ==== TCAM MMIO Implementation ====

static uint32_t tcam_result = 0;
static uint32_t last_query_addr = 0;

static inline uint64_t read_cycle() {
    uint64_t c;
    asm volatile ("rdcycle %0" : "=r"(c));
    return c;
}

static inline void wait_cycles(uint64_t num_cycles) {
    uint64_t start = read_cycle();
    while ((read_cycle() - start) < num_cycles) {
        // busy wait
    }
}

void delay_write() {
    for (volatile int i = 0; i < 1; i++); 
}

void delay_read() {
    for (volatile int i = 0; i < 100000; i++); 
}

uint32_t tcam_write(uint32_t address, uint32_t wdata, uint8_t in_web) {
    uint32_t wmask = 0xF; // write all bits
    uint8_t in_csb = 0;   // always active

    uint64_t rs1 = ((uint64_t)(wmask & 0xF) << 28) | (address & 0x0FFFFFFF);
    uint64_t rs2 = (uint64_t)wdata;
    uint64_t result;

    // Manually dispatch based on funct value
    if (in_web == 0 && in_csb == 0) {
        ROCC_INSTRUCTION_DSS(0, result, rs1, rs2, 0); // funct = 0b00
    } else if (in_web == 0 && in_csb == 1) {
        ROCC_INSTRUCTION_DSS(0, result, rs1, rs2, 1); // funct = 0b01
    } else if (in_web == 1 && in_csb == 0) {
        ROCC_INSTRUCTION_DSS(0, result, rs1, rs2, 2); // funct = 0b10
    } else if (in_web == 1 && in_csb == 1) {
        ROCC_INSTRUCTION_DSS(0, result, rs1, rs2, 3); // funct = 0b11
    }
    return (uint32_t)result;
}


void write_tcam(uint32_t tcam_addr,uint32_t wdata) {    
    tcam_write(tcam_addr, wdata, /*in_web=*/0);  // web=0 (write)
    delay_write();
}

void search_tcam(uint32_t query) {
    tcam_write( query, 0,/*in_web=*/1);  // web=1 (read), address = 0
    last_query_addr = query;
    delay_read();
}

uint32_t read_tcam_status() {
    tcam_result = tcam_write( last_query_addr, 0,/*in_web=*/1);
    return tcam_result;
}

// ==== Test Application ====

int main() {
    printf("=== TCAM RoCC Test ===\n");

    // Populate TCAM with example values
    write_tcam(0x00000005,0x00000010);
    write_tcam(0x00000085,0x00000000);
    write_tcam(0x00000105,0x00000010);
    write_tcam(0x00000185,0x00000000);
    write_tcam(0x00000205,0x00000010);
    write_tcam(0x00000285,0x00000000);
    write_tcam(0x00000305,0x00000010);
    write_tcam(0x00000385,0x00000000);

    // Search for a value in TCAM
    uint32_t search_query = 0x00A14285;
    search_tcam(search_query);
    delay_read();
    read_tcam_status();
    // Wait enough cycles so that all 8 VTB dout reads (debug prints) complete before printing status from Verilog
    wait_cycles(200000);

    // Intentionally do not print the status here to avoid interleaving with Verilog $display output.
    // The status is printed in Verilog (TCAMBlackBox.sv) one cycle after the debug reads.

    return 0;
}

