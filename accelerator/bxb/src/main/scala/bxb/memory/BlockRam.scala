package bxb.memory

import chisel3._
import bxb.util.{Util}
import chisel3.util.HasBlackBoxInline
import chisel3.experimental._

// XXX: this one supposed to be replaced with black box
// verilog (direct mega function instantiation or something later)
// for now let's consider it to be a behavioral model

class BlockRam(size: Int, width: Int) extends Module {
  val addrWidth = Chisel.log2Up(size)
  val io = IO(new Bundle {
    val read = Input(ReadPort(addrWidth))
    val write = Input(WritePort(addrWidth, width))
    val readQ = Output(UInt(width.W))
  })
  //val bram_inst = Module(new BRam(size, width))

  val bram_inst = Module(new BlackBox(Map(
      "SIZE" -> size,
      "WIDTH" -> width,
      "ADDR_WIDTH" -> addrWidth
    )) with HasBlackBoxInline{

    override def desiredName = "BRam"

    val addrWidth = Chisel.log2Up(size)
    val io = IO(new Bundle {
      val clock   = Input(Clock())
      val reset   = Input(Bool())
      val wen     = Input(Bool())
      val wrAddr  = Input(UInt(addrWidth.W))
      val wrData  = Input(UInt(width.W))
      val rdAddr  = Input(UInt(addrWidth.W))
      val rdData  = Output(UInt(width.W))
    })
    // val BRam_name = "BRam" + size
    setInline("BRam.v",
    f"""
      |module BRam #(
      |  parameter SIZE,
      |  parameter WIDTH,
      |  parameter ADDR_WIDTH) (
      |
      |input                    clock,
      |input                    reset,
      |input                    wen,
      |input  [ADDR_WIDTH-1:0]  wrAddr,
      |input  [WIDTH-1:0]      wrData,
      |input  [ADDR_WIDTH-1:0]  rdAddr,
      |output reg [WIDTH-1:0]  rdData
      |);
      |reg [WIDTH-1:0] mem [0:SIZE-1];
      |always @ (posedge clock)
      |begin
      |  // Write
      |  if (wen)
      |    mem[wrAddr] <= wrData;
      |
      |  // Read (if rdAddr == wrAddr, return OLD data)_	To return
      |  // NEW data, use = (blocking write) rather than <= (non-blocking write)
      |  // in the write assignment_	 NOTE: NEW data may require extra bypass
      |  // logic around the mem_
      |  rdData <= mem[rdAddr];
      |end
      |endmodule
    """.stripMargin)

  })

  bram_inst.io.clock  := clock
  bram_inst.io.reset  := reset
  bram_inst.io.wen    := io.write.enable
  bram_inst.io.wrAddr := io.write.addr
  bram_inst.io.wrData := io.write.data
  bram_inst.io.rdAddr := io.read.addr
  io.readQ            := bram_inst.io.rdData
}

object BlockRam {
  def main(args: Array[String]): Unit = {
    println(Util.getVerilog(new BlockRam(4096, 2)))
  }
}
