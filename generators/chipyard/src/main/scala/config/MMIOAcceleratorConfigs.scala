package chipyard

import org.chipsalliance.cde.config.{Config}

// ------------------------------
// Configs with MMIO accelerators
// ------------------------------

// DOC include start: FFTRocketConfig
class FFTRocketConfig extends Config(
  new fftgenerator.WithFFTGenerator(numPoints=8, width=16, decPt=8) ++ // add 8-point mmio fft at the default addr (0x2400) with 16bit fixed-point numbers.
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: FFTRocketConfig

// DOC include start: GCDTLRocketConfig
class GCDTLRocketConfig extends Config(
  new chipyard.example.WithGCD(useAXI4=false, useBlackBox=false) ++          // Use GCD Chisel, connect Tilelink
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: GCDTLRocketConfig

// DOC include start: GCDAXI4BlackBoxRocketConfig
class GCDAXI4BlackBoxRocketConfig extends Config(
  new chipyard.example.WithGCD(useAXI4=true, useBlackBox=true) ++            // Use GCD blackboxed verilog, connect by AXI4->Tilelink
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: GCDAXI4BlackBoxRocketConfig

class GCDHLSRocketConfig extends Config(
  new chipyard.example.WithGCD(useAXI4=false, useBlackBox=false, useHLS=true) ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

class GCDExternallyClockedRocketConfig extends Config(
  new chipyard.example.WithGCD(externallyClocked=true)++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

// DOC include start: InitZeroRocketConfig
class InitZeroRocketConfig extends Config(
  new chipyard.example.WithInitZero(0x88000000L, 0x1000L) ++   // add InitZero
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: InitZeroRocketConfig

class StreamingPassthroughRocketConfig extends Config(
  new chipyard.example.WithStreamingPassthrough ++          // use top with tilelink-controlled streaming passthrough
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

// DOC include start: StreamingFIRRocketConfig
class StreamingFIRRocketConfig extends Config (
  new chipyard.example.WithStreamingFIR ++                  // use top with tilelink-controlled streaming FIR
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: StreamingFIRRocketConfig

class SmallNVDLARocketConfig extends Config(
  new nvidia.blocks.dla.WithNVDLA("small") ++               // add a small NVDLA
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

class LargeNVDLARocketConfig extends Config(
  new nvidia.blocks.dla.WithNVDLA("large", true) ++         // add a large NVDLA with synth. rams
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

class ManyMMIOAcceleratorRocketConfig extends Config(
  new chipyard.example.WithInitZero(0x88000000L, 0x1000L) ++   // add InitZero
  new fftgenerator.WithFFTGenerator(numPoints=8, width=16, decPt=8) ++ // add 8-point mmio fft at the default addr (0x2400) with 16bit fixed-point numbers.
  new chipyard.example.WithStreamingPassthrough ++          // use top with tilelink-controlled streaming passthrough
  new chipyard.example.WithStreamingFIR ++                  // use top with tilelink-controlled streaming FIR
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

// 64x28 TCAM MMIO config
class Tcam64x28Config extends Config(
  new chipyard.harness.WithSimAXIMem ++                     
  new tcam.WithTCAM64x28 ++  // 64x28 configuration
  new freechips.rocketchip.rocket.WithNSmallCores(1) ++    
  new chipyard.config.WithPeripheryBusFrequency(100.0) ++
  new chipyard.config.WithSystemBusFrequency(100.0) ++
  new chipyard.config.WithFrontBusFrequency(100.0) ++
  new chipyard.config.WithMemoryBusFrequency(100.0) ++
  new chipyard.config.WithControlBusFrequency(100.0) ++
  new chipyard.config.WithOffchipBusFrequency(100.0) ++
  new chipyard.config.AbstractConfig)

// 32x28 TCAM MMIO config
class Tcam32x28Config extends Config(
  new chipyard.harness.WithSimAXIMem ++                     
  new tcam.WithTCAM32x28 ++  // 32x28 configuration
  new freechips.rocketchip.rocket.WithNSmallCores(1) ++    
  new chipyard.config.WithPeripheryBusFrequency(100.0) ++
  new chipyard.config.WithSystemBusFrequency(100.0) ++
  new chipyard.config.WithFrontBusFrequency(100.0) ++
  new chipyard.config.WithMemoryBusFrequency(100.0) ++
  new chipyard.config.WithControlBusFrequency(100.0) ++
  new chipyard.config.WithOffchipBusFrequency(100.0) ++
  new chipyard.config.AbstractConfig)

// 64x28 TCAM AXI4 config
class Tcam64x28AXI4Config extends Config(
  new chipyard.harness.WithSimAXIMem ++                     
  new tcam.WithTCAM64x28AXI4 ++  // 64x28 AXI4 configuration
  new freechips.rocketchip.rocket.WithNSmallCores(1) ++    
  new chipyard.config.WithPeripheryBusFrequency(100.0) ++
  new chipyard.config.WithSystemBusFrequency(100.0) ++
  new chipyard.config.WithFrontBusFrequency(100.0) ++
  new chipyard.config.WithMemoryBusFrequency(100.0) ++
  new chipyard.config.WithControlBusFrequency(100.0) ++
  new chipyard.config.WithOffchipBusFrequency(100.0) ++
  new chipyard.config.AbstractConfig)

// 32x28 TCAM AXI4 config
class Tcam32x28AXI4Config extends Config(
  new chipyard.harness.WithSimAXIMem ++                     
  new tcam.WithTCAM32x28AXI4 ++  // 32x28 AXI4 configuration
  new freechips.rocketchip.rocket.WithNSmallCores(1) ++    
  new chipyard.config.WithPeripheryBusFrequency(100.0) ++
  new chipyard.config.WithSystemBusFrequency(100.0) ++
  new chipyard.config.WithFrontBusFrequency(100.0) ++
  new chipyard.config.WithMemoryBusFrequency(100.0) ++
  new chipyard.config.WithControlBusFrequency(100.0) ++
  new chipyard.config.WithOffchipBusFrequency(100.0) ++
  new chipyard.config.AbstractConfig)
