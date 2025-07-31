#include "tcam_mmio.h"

int main() {
    printf("=== TCAM C Test ===\n");

    write_tcam(0x00000010, 0x00000005);
    write_tcam(0x00000000, 0x00000085);
    write_tcam(0x00000010, 0x00000105);
    write_tcam(0x00000000, 0x00000185);
    write_tcam(0x00000010, 0x00000205);
    write_tcam(0x00000000, 0x00000285);
    write_tcam(0x00000010, 0x00000305);
    write_tcam(0x00000000, 0x00000385);

    
    uint32_t search_query = 0x00A14285; 
  
    search_tcam(search_query); 
    printf("TCAM read status: 0x%08X\n", read_tcam_status());

    return 0;
}
