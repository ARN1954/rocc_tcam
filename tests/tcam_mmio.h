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
    for (volatile int i = 0; i < 1; i++); 
}

void delay_read() {
    for (volatile int i = 0; i < 2; i++); 
}

// Write a value to the TCAM at a given address
// csb=0, web=0, wmask=0xF
void write_tcam(uint32_t data, uint32_t address) {
    reg_write32(TCAM_CONTROL, 0xF3); // csb=0, web=0, wmask=0xF
    reg_write32(TCAM_ADDRESS, address);
    reg_write32(TCAM_WDATA, data);
    delay_write(); 
}

// Write an array to TCAM with sequential addresses starting from 0
// Works directly with arrays, no pointers
#define write_tcam_array(data_array) do { \
    int size = sizeof(data_array) / sizeof(data_array[0]); \
    for(int i = 0; i < size; i++) { \
        write_tcam(data_array[i], i); \
    } \
} while(0)

// Search the TCAM with a query value
// csb=0, web=1, wmask=0
void search_tcam(uint32_t search_query) {
    reg_write32(TCAM_CONTROL, 0x01); // csb=0, web=1, wmask=0
    reg_write32(TCAM_ADDRESS, search_query);
    delay_read(); 
}

// Read the TCAM status (priority match address)
uint32_t read_tcam_status() {
    return reg_read32(TCAM_STATUS);
}

#endif // __TCAM_MMIO_H__
