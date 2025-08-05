package com.tcam.generator

import scala.util.{Success, Failure}

/**
 * Main entry point for TCAM RTL generation
 */
object Main {
  
  def main(args: Array[String]): Unit = {
    if (args.length < 2) {
      println("Usage: sbt \"run <width> <depth> [output_dir]\"")
      println("Example: sbt \"run 64 28 generated_rtl\"")
      System.exit(1)
    }
    
    val width = args(0).toInt
    val depth = args(1).toInt
    val outputDir = if (args.length > 2) args(2) else "generated_rtl"
    
    TCAMRTLGenerator.generate(width, depth, outputDir) match {
      case Success(outputPath) =>
        println(s"✅ TCAM RTL generated successfully at: $outputPath")
      case Failure(exception) =>
        println(s"❌ Failed to generate TCAM RTL: ${exception.getMessage}")
        System.exit(1)
    }
  }
} 