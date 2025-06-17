#include <stdint.h>
#include <stdio.h>
#include "mmio.h"

#define TCAM_BASE      0x4000
#define TCAM_STATUS    (TCAM_BASE + 0x00)  
#define TCAM_CONTROL   (TCAM_BASE + 0x04)  
#define TCAM_WDATA     (TCAM_BASE + 0x08)  
#define TCAM_ADDRESS   (TCAM_BASE + 0x0C)  

void delay() {
    for (volatile int i = 0; i < 100; i++);
}

int main() {
    printf("=== TCAM Minimal Test ===\n");
    
    // 1. Verify CONTROL register
    printf("Testing CONTROL register...\n");
    uint32_t test_val = 0xA5;
    reg_write32(TCAM_CONTROL, test_val);
    delay();
    
    // 2. Verify WDATA register
    printf("Testing WDATA register...\n");
    reg_write32(TCAM_WDATA, 0x12345678);
    delay();
    
    // 3. Verify ADDRESS register
    printf("Testing ADDRESS register...\n");
    reg_write32(TCAM_ADDRESS, 0x100);
    delay();
    
    // 4. Read STATUS (shouldn't fail)
    printf("Reading STATUS register...\n");
    uint32_t status = reg_read32(TCAM_STATUS);
    printf("STATUS read: 0x%08X\n", status);
    
    return 0;
}

