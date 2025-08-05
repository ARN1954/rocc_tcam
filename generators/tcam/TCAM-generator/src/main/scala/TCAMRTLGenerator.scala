package com.tcam.generator

import scala.util.{Try, Success, Failure}
import java.io.File
import scala.sys.process._

/**
 * TCAM RTL Generator using OpenTCAM
 * 
 * Generates RTL files based on configuration parameters
 * using OpenTCAM's existing configuration system.
 */
object TCAMRTLGenerator {
  
  case class TCAMConfig(
    width: Int,
    depth: Int,
    priority: Boolean = true,
    outputDir: String = "generated_rtl",
    timeUnit: String = "1ns",
    timePrecision: String = "100ps"
  )
  
  /**
   * Generate TCAM RTL based on configuration
   */
  def generateRTL(config: TCAMConfig): Try[String] = {
    Try {
      // Create output directory
      val outputDir = new File(config.outputDir)
      if (!outputDir.exists()) {
        outputDir.mkdirs()
      }
      
      // Map dimensions to OpenTCAM config name
      val configName = mapToOpenTCAMConfig(config.width, config.depth)
      
      println(s"Generating TCAM RTL: ${config.width}x${config.depth}")
      println(s"Using OpenTCAM config: $configName")
      println(s"Output directory: ${config.outputDir}")
      
      // Run OpenTCAM with proper parameters
      val command = s"python3 compiler/src/mainTcamRTLGenerator.py --tcamWrapConfig $configName --timeunit ${config.timeUnit} --timeprecision ${config.timePrecision}"
      val result = Process(command, new File("OpenTCAM")).!
      
      if (result != 0) {
        throw new RuntimeException(s"OpenTCAM generation failed with exit code: $result")
      }
      
      // Copy generated wrapper file - OpenTCAM generates top_tcam_mem_64x28.sv with TCAMBlackBox module inside
      val generatedWrapper = new File(s"OpenTCAM/tcam_mem_rtl/${configName}/top_tcam_mem_${config.width}x${config.depth}.sv")
      val outputWrapper = new File(config.outputDir, s"TCAMBlackBox.sv")
      
      if (!generatedWrapper.exists()) {
        throw new RuntimeException(s"Generated wrapper file not found: ${generatedWrapper.getAbsolutePath}")
      }
      
      // Copy wrapper file
      java.nio.file.Files.copy(
        generatedWrapper.toPath, 
        outputWrapper.toPath, 
        java.nio.file.StandardCopyOption.REPLACE_EXISTING
      )
      
      // Copy RTL blocks to output directory
      copyRTLBlocks(config.outputDir, configName)
      
      println(s"✅ TCAM RTL generated successfully")
      println(s"📁 Main wrapper: ${outputWrapper.getAbsolutePath}")
      println(s"📁 RTL blocks copied to: ${config.outputDir}")
      
      outputWrapper.getAbsolutePath
    }
  }
  
  /**
   * Copy OpenTCAM RTL blocks to output directory
   */
  def copyRTLBlocks(outputDir: String, configName: String): Unit = {
    val rtlBlocks = Seq(
      "tcam_7x64.sv",
      "and_gate.sv", 
      "priority_encoder_64x6.sv",
      "sky130_sram_1kbyte_1rw1r_32x256_8.sv"
    )
    
    rtlBlocks.foreach { block =>
      val srcFile = new File(s"OpenTCAM/tcam_mem_rtl/${configName}/$block")
      val dstFile = new File(s"$outputDir/$block")
      
      if (srcFile.exists()) {
        java.nio.file.Files.copy(
          srcFile.toPath,
          dstFile.toPath,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING
        )
      }
    }
  }
  
  /**
   * Map TCAM dimensions to OpenTCAM configuration names
   */
  def mapToOpenTCAMConfig(width: Int, depth: Int): String = {
    (width, depth) match {
      case (64, 28) => "tcamMemWrapper_64x28"
      case _ => 
        println(s"⚠️  Warning: No specific config for ${width}x${depth}, using default tcamMemWrapper_64x28")
        "tcamMemWrapper_64x28"
    }
  }
  
  /**
   * Generate RTL with simple parameters
   */
  def generate(width: Int, depth: Int, outputDir: String = "generated_rtl"): Try[String] = {
    val config = TCAMConfig(width, depth, true, outputDir)
    generateRTL(config)
  }
} 