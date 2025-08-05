package tcam

import java.io.{File, FileWriter, PrintWriter}
import scala.sys.process._
import scala.util.{Try, Success, Failure}

// Enhanced RTL Generator for TCAM with dynamic generation using OpenTCAM
class TCAMRTLGenerator(params: TCAMParams) {
  
  def generateRTL(): Unit = {
    val chipyardDir = System.getProperty("user.dir")
    val tcamDir = s"$chipyardDir/generators/tcam/src/main/resources/vsrc/tcam"
    
    // Create directory if it doesn't exist
    new File(tcamDir).mkdirs()
    
    // Check if we're using the specific tcam_64x28 configuration
    val isTCAM64x28 = params.keyWidth == 28 && params.entries == 64 && 
                       params.tableConfig.exists(tc => tc.queryStrLen == 28 && tc.potMatchAddr == 64)
    
    if (isTCAM64x28) {
      // Use dynamic generation for 64x28 TCAM
      generateDynamicTCAM64x28(tcamDir)
    } else {
      // For now, only support 64x28 TCAM with dynamic generation
      throw new IllegalArgumentException(s"Unsupported TCAM configuration: keyWidth=${params.keyWidth}, entries=${params.entries}. Only tcam_64x28 is supported with dynamic generation.")
    }
    
    println(s"Generated TCAM RTL in: $tcamDir")
  }
  
  private def generateDynamicTCAM64x28(outputDir: String): Unit = {
    println("Using dynamic TCAM generation for 64x28 configuration...")
    
    // Path to the TCAM-generator
    val tcamGeneratorDir = s"${System.getProperty("user.dir")}/generators/tcam/TCAM-generator"
    val tcamGenerator = new File(tcamGeneratorDir)
    
    if (!tcamGenerator.exists()) {
      throw new RuntimeException(s"TCAM-generator not found at: $tcamGeneratorDir")
    }
    
    // Generate RTL using the TCAM-generator
    val command = Seq("bash", "-c", s"cd $tcamGeneratorDir && sbt \"run 64 28 temp_generated_rtl\"")
    println(s"Executing: ${command.mkString(" ")}")
    
    val result = Process(command, new File(".")).!
    
    if (result != 0) {
      throw new RuntimeException(s"TCAM-generator failed with exit code: $result")
    }
    
    // Copy generated files to the expected location
    copyGeneratedFiles(s"$tcamGeneratorDir/temp_generated_rtl", outputDir)
    
    // Clean up temporary directory
    Process(Seq("bash", "-c", s"rm -rf $tcamGeneratorDir/temp_generated_rtl"), new File(".")).!
    
    println("✅ Dynamic TCAM 64x28 generation completed successfully")
  }
  
  private def copyGeneratedFiles(srcDir: String, dstDir: String): Unit = {
    val src = new File(srcDir)
    val dst = new File(dstDir)
    
    if (!src.exists()) {
      throw new RuntimeException(s"Generated files not found at: $srcDir")
    }
    
    // Copy all .sv files with proper naming
    src.listFiles().filter(_.getName.endsWith(".sv")).foreach { file =>
      val fileName = file.getName
      val destFileName = fileName match {
        case "tcam7x64.sv" => "tcam7x64.sv"  // Keep original name as expected by BlackBox
        case "TCAMBlackBox.sv" => "TCAMBlackBox.sv"  // Keep original name
        case "priority_encoder_64x6.sv" => "priority_encoder_64x6.sv"  // Keep original name
        case "and_gate.sv" => "and_gate.sv"  // Keep original name
        case "sky130_sram_1kbyte_1rw1r_32x256_8.sv" => "sky130_sram_1kbyte_1rw1r_32x256_8.sv"  // Keep original name
        case _ => fileName  // Keep other files as-is
      }
      
      val destFile = new File(dst, destFileName)
      java.nio.file.Files.copy(
        file.toPath,
        destFile.toPath,
        java.nio.file.StandardCopyOption.REPLACE_EXISTING
      )
      println(s"Copied: ${fileName} -> ${destFileName}")
    }
  }
} 