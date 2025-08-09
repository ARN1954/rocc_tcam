#ifndef __TCAM_MMIO_H__
#define __TCAM_MMIO_H__

#include <stdint.h>
#include <stdio.h>
#include "mmio.h"

// TCAM register base and offsets
#define TCAM_BASE      0x4000
#define TCAM_STATUS    (TCAM_BASE + 0x00)
#define TCAM_CONTROL   (TCAM_BASE + 0x04)
#define TCAM_WDATA     (TCAM_BASE + 0x08)
#define TCAM_ADDRESS   (TCAM_BASE + 0x0C)

// Delay functions for timing
void delay_write() {
    for (volatile int i = 0; i < 1000; i++); 
}

void delay_read() {
    for (volatile int i = 0; i < 1000; i++); 
}

// Write a value to the TCAM at a given address
// Sequence: set address+data -> assert (csb=0, web=0, wmask=0xF) -> wait -> deassert csb
void write_tcam(uint32_t data, uint32_t address) {
    // Program address and data first
    reg_write32(TCAM_ADDRESS, address);
    reg_write32(TCAM_WDATA, data);
    // Assert control: csb=0 (bit0=1), web=0 (bit1=1), wmask=0xF (bits7..4=1111)
    reg_write32(TCAM_CONTROL, 0xF3);
    delay_write();
    // Deassert csb while keeping other fields (bit0=0 -> in_csb=1)
    reg_write32(TCAM_CONTROL, 0xF2);
}

// Search the TCAM with a query value
// Sequence: set address -> assert (csb=0, web=1) -> wait -> optionally deassert csb
void search_tcam(uint32_t search_query) {
    // Program query address first
    reg_write32(TCAM_ADDRESS, search_query);
    // Assert control: csb=0 (bit0=1), web=1 (bit1=0), wmask=0 (bits7..4)
    reg_write32(TCAM_CONTROL, 0x01);
    delay_read();
    // Deassert csb after allowing one cycle for read path
    reg_write32(TCAM_CONTROL, 0x00);
}

// Read the TCAM status (priority match address)
uint32_t read_tcam_status() {
    return reg_read32(TCAM_STATUS);
}

#endif // __TCAM_MMIO_H__
