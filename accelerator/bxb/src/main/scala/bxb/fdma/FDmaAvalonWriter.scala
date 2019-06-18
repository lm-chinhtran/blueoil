package bxb.fdma

import chisel3._
import chisel3.util._

import bxb.util.{Util}

class FDmaAvalonWriter(avalonAddrWidth: Int, avalonDataWidth: Int, dataWidth: Int, tileCountWidth: Int, maxBurst: Int) extends Module {
  require(isPow2(maxBurst))
  require(isPow2(avalonDataWidth))
  require(isPow2(dataWidth))
  require(avalonDataWidth < dataWidth)

  val avalonDataByteWidth = avalonDataWidth / 8
  val avalonDataByteWidthLog = Chisel.log2Up(avalonDataByteWidth)

  val wordsPerElement = dataWidth / avalonDataWidth
  val wordsPerElementLog = Chisel.log2Floor(wordsPerElement)

  require(maxBurst >= wordsPerElement)
  val elementsPerBurstMax = maxBurst / wordsPerElement

  val countMsb = Chisel.log2Floor(elementsPerBurstMax)
  val burstMsb = Chisel.log2Floor(maxBurst)

  val io = IO(new Bundle {
    // Tile Generator interface
    val tileStartAddress = Input(UInt(avalonAddrWidth.W))
    val tileHeight = Input(UInt(tileCountWidth.W))
    val tileWidth = Input(UInt(tileCountWidth.W))
    // byte distance between last element of some row and
    // first element of a row adjacent to it in elements
    val tileWordRowToRowDistance = Input(UInt(tileCountWidth.W))
    // once tileValid asserted above tile parameters must remain stable and until tileAccepted is asserted
    val tileValid = Input(Bool())
    // accepted at a clock when last request is sent
    val tileAccepted = Output(Bool())

    // Avalon interface
    val avalonMasterAddress = Output(UInt(avalonAddrWidth.W))
    val avalonMasterBurstCount = Output(UInt(10.W))
    val avalonMasterWaitRequest = Input(Bool())
    val avalonMasterWrite = Output(Bool())

    // FDmaReader interface
    val readerReady = Input(Bool())
  })

  private def toBytes(words: UInt) = {
    words << avalonDataByteWidthLog.U
  }

  private def toWords(elements: UInt) = {
    elements << wordsPerElementLog.U
  }

  object State {
    val idle :: runningLong :: runningShort :: acknowledge :: Nil = Enum(4)
  }

  val state = RegInit(State.idle)

  val idle = (state === State.idle)
  val runningLong = (state === State.runningLong)
  val runningShort = (state === State.runningShort)
  val running = (runningLong | runningShort)
  val acknowledge = (state === State.acknowledge)

  val waitRequired = (running & io.avalonMasterWaitRequest)

  // tile loops
  val tileXCountShort = if (countMsb != 0) toWords(io.tileWidth(countMsb - 1, 0)) else 0.U(1.W)
  val tileXCountShortZero = (tileXCountShort === 0.U)
  val tileXCountShortLeft = Reg(UInt(burstMsb.W))
  val tileXCountShortLast = (tileXCountShortLeft === 1.U)
  when(~waitRequired) {
    when(~runningShort | tileXCountShortLast) {
      tileXCountShortLeft := tileXCountShort
    }.otherwise {
      tileXCountShortLeft := tileXCountShortLeft - 1.U
    }
  }

  // when maxBurst > 1, two counters are used
  //  tileXCountLongLeft - is number of transactions remained
  //  tileXCountLongLoLeft - is number of data transfer remained in current transaction
  // when maxBurst == 1, tileXCountLongLeft is only counter we need
  //  as each transaction has only one element
  val tileXCountLongLoLeft = Reg(UInt((burstMsb + 1).W))
  val tileXCountLongLoLast = if (burstMsb != 0) (tileXCountLongLoLeft === 1.U) else true.B
  if (burstMsb != 0) {
    when(~waitRequired) {
      when(~runningLong | tileXCountLongLoLast) {
        tileXCountLongLoLeft := maxBurst.U
      }.otherwise {
        tileXCountLongLoLeft := tileXCountLongLoLeft - 1.U
      }
    }
  }

  val tileXCountLong = io.tileWidth(tileCountWidth - 1, countMsb)
  val tileXCountLongZero = (tileXCountLong === 0.U)
  val tileXCountLongLeft = Reg(UInt((tileCountWidth - countMsb + wordsPerElementLog).W))
  val tileXCountLongLast = (tileXCountLongLeft === 1.U) & tileXCountLongLoLast
  val tileXCountLast = ((runningShort & tileXCountShortLast) | (runningLong & tileXCountLongLast & tileXCountShortZero))
  when(~waitRequired) {
    when(~runningLong | tileXCountLongLast) {
      tileXCountLongLeft := tileXCountLong
    }.elsewhen(tileXCountLongLoLast) {
      tileXCountLongLeft := tileXCountLongLeft - 1.U
    }
  }

  val tileYCountLeft = Reg(UInt(tileCountWidth.W))
  val tileYCountLast = (tileYCountLeft === 1.U) & tileXCountLast
  when(~waitRequired) {
    when(idle | tileYCountLast) {
      tileYCountLeft := io.tileHeight
    }.elsewhen(tileXCountLast) {
      tileYCountLeft := tileYCountLeft - 1.U
    }
  }

  val avalonAddress = Reg(UInt(avalonAddrWidth.W))
  when(~waitRequired) {
    when(idle) {
      avalonAddress := io.tileStartAddress
    }.elsewhen(tileXCountLast) {
      avalonAddress := avalonAddress + toBytes(io.tileWordRowToRowDistance)
    }.otherwise {
      avalonAddress := avalonAddress + toBytes(1.U)
    }
  }

  when(~waitRequired) {
    when(idle & io.tileValid & io.readerReady) {
      when(~tileXCountLongZero) {
        state := State.runningLong
      }.elsewhen(~tileXCountShortZero) {
        state := State.runningShort
      }
    }.elsewhen(runningLong & tileXCountLongLast) {
      when(tileYCountLast) {
        state := State.acknowledge
      }.elsewhen(~tileXCountShortZero) {
        state := State.runningShort
      }
    }.elsewhen(runningShort & tileXCountShortLast) {
      when(tileYCountLast) {
        state := State.acknowledge
      }.elsewhen(~tileXCountLongZero) {
        state := State.runningLong
      }
    }.elsewhen(acknowledge) {
      state := State.idle
    }
  }

  io.tileAccepted := acknowledge
  io.avalonMasterAddress := avalonAddress
  io.avalonMasterWrite := running
  io.avalonMasterBurstCount := Mux(runningShort, tileXCountShort, maxBurst.U)
}

object FDmaAvalonWriter {
  def main(args: Array[String]): Unit = {
    println(Util.getVerilog(new FDmaAvalonWriter(32, 128, 512, 12, 16)))
  }
}