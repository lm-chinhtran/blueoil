package bxb.rdma

import chisel3._
import chisel3.util._

import bxb.util.{Util}

class RDmaTileGenerator(avalonAddrWidth: Int, avalonDataWidth: Int, tileCountWidth: Int) extends Module {
  require(isPow2(avalonDataWidth))
  val avalonDataByteWidth = avalonDataWidth / 8
  val avalonDataByteWidthLog = Chisel.log2Ceil(avalonDataByteWidth)
  val io = IO(new Bundle {
    val start = Input(Bool())

    // Tile generation parameters
    val outputAddress = Input(UInt(avalonAddrWidth.W))
    // - should be equal to roundUp(outputHeight / tileHeight)
    val outputHCount = Input(UInt(6.W))
    // - should be equal to roundUp(outputWidth / tileWidth)
    val outputWCount = Input(UInt(6.W))
    // - should be equal to roundUp(outputChannels / B)
    val outputCCount = Input(UInt(6.W))

    // tileHeight
    val regularTileH = Input(UInt(tileCountWidth.W))
    // - outputHeight - (hCount - 1)  * tileHeight
    val lastTileH = Input(UInt(tileCountWidth.W))

    // tileWidth
    val regularTileW = Input(UInt(tileCountWidth.W))
    // - outputWidth - (wCount - 1)  * tileWidth
    val lastTileW = Input(UInt(tileCountWidth.W))

    // (outputWidth - regularTileW + (regularTileW % maxBurst == 0) ? maxBurst : regularTileW % maxBurst)
    val regularRowToRowDistance = Input(UInt(tileCountWidth.W))
    // (outputWidth - lastTileW + (lastTileW % maxBurst == 0) ? maxBurst : lastTileW % maxBurst)
    val lastRowToRowDistance = Input(UInt(tileCountWidth.W))

    // outputHeight * outputWidth
    val outputSpace = Input(UInt(avalonAddrWidth.W))

    // outputWidth * regularTileH - outputWidth + lastTileW
    val rowDistance = Input(UInt(avalonAddrWidth.W))

    // Tile output interface with handshaking
    val tileStartAddress = Output(UInt(avalonAddrWidth.W))
    val tileHeight = Output(UInt(tileCountWidth.W))
    val tileWidth = Output(UInt(tileCountWidth.W))
    val tileRowToRowDistance = Output(UInt(tileCountWidth.W))
    val tileFirst = Output(Bool())
    val tileValid = Output(Bool())
    val tileAccepted = Input(Bool())

    // Synchronization interface
    val rRawDec = Output(Bool())
    val rRawZero = Input(Bool())

    // Status
    val statusReady = Output(Bool())
  })

  private def toBytes(elements: UInt) = {
    elements << avalonDataByteWidthLog.U
  }

  object State {
    val idle :: resetCounters :: updateCounters :: setupTile :: waitSync :: valid :: Nil = Enum(6)
  }

  val state = RegInit(State.idle)
  val idle = (state === State.idle)
  val resetCounters = (state === State.resetCounters)
  val updateCounters = (state === State.updateCounters)
  val setupTile = (state === State.setupTile)
  val waitSync = (state === State.waitSync)
  val valid = (state === State.valid)

  val syncDecRRaw = (setupTile | waitSync)

  val tileFirst = Reg(Bool())
  when(idle) {
    tileFirst := true.B
  }.elsewhen(valid & io.tileAccepted) {
    tileFirst := false.B
  }

  val outputCCountLeft = Reg(UInt(tileCountWidth.W))
  val outputCCountLast = (outputCCountLeft === 1.U)
  when(resetCounters) {
    outputCCountLeft := io.outputCCount
  }.elsewhen(updateCounters) {
    when(outputCCountLast) {
      outputCCountLeft := io.outputCCount
    }.otherwise {
      outputCCountLeft := outputCCountLeft - 1.U
    }
  }

  object DepthState {
    val first :: rest :: Nil = Enum(2)
  }

  val depthState = RegInit(DepthState.first)
  val depthFirst = (depthState === DepthState.first)
  when(resetCounters) {
    depthState := DepthState.first
  }.elsewhen(updateCounters) {
    when(outputCCountLast) {
      depthState := DepthState.first
    }.elsewhen(depthFirst) {
      depthState := DepthState.rest
    }
  }

  val outputWCountLeft = Reg(UInt(tileCountWidth.W))
  val outputWCountOne = (outputWCountLeft === 1.U)
  val outputWCountLast = outputWCountOne & outputCCountLast
  when(resetCounters) {
    outputWCountLeft := io.outputWCount
  }.elsewhen(updateCounters & outputCCountLast) {
    when(outputWCountLast) {
      outputWCountLeft := io.outputWCount
    }.otherwise {
      outputWCountLeft := outputWCountLeft - 1.U
    }
  }
  val horizontalLast = outputWCountOne

  val outputHCountLeft = Reg(UInt(tileCountWidth.W))
  val outputHCountOne = (outputHCountLeft === 1.U)
  val outputHCountLast = outputHCountOne & outputWCountLast
  when(resetCounters) {
    outputHCountLeft := io.outputHCount
  }.elsewhen(updateCounters & outputWCountLast) {
    when(outputHCountLast) {
      outputHCountLeft := io.outputHCount
    }.otherwise {
      outputHCountLeft := outputHCountLeft - 1.U
    }
  }
  val verticalLast = outputHCountOne

  val tileFirstChannelAddress = Reg(UInt(avalonAddrWidth.W))
  when(resetCounters) {
    tileFirstChannelAddress := io.outputAddress
  }.elsewhen(updateCounters & outputCCountLast) {
    when(~horizontalLast) {
      tileFirstChannelAddress := tileFirstChannelAddress + toBytes(io.tileWidth)
    }.otherwise {
      tileFirstChannelAddress := tileFirstChannelAddress + toBytes(io.rowDistance)
    }
  }

  val tileAddress = Reg(UInt(avalonAddrWidth.W))
  when(setupTile) {
    when(depthFirst) {
      tileAddress := tileFirstChannelAddress
    }.otherwise {
      tileAddress := tileAddress + toBytes(io.outputSpace)
    }
  }

  val tileWidth = Reg(UInt(tileCountWidth.W))
  when(setupTile) {
    when(~horizontalLast) {
      tileWidth := io.regularTileW
    }.otherwise {
      tileWidth := io.lastTileW
    }
  }

  val tileHeight = Reg(UInt(tileCountWidth.W))
  when(setupTile) {
    when(~verticalLast) {
      tileHeight := io.regularTileH
    }.otherwise {
      tileHeight := io.lastTileH
    }
  }

  val tileRowToRowDistance = Reg(UInt(tileCountWidth.W))
  when(setupTile) {
    when(~horizontalLast) {
      tileRowToRowDistance := io.regularRowToRowDistance
    }.otherwise {
      tileRowToRowDistance := io.lastRowToRowDistance
    }
  }

  when(idle & io.start) {
    state := State.resetCounters
  }.elsewhen(resetCounters) {
    state := State.setupTile
  }.elsewhen(updateCounters) {
    state := State.setupTile
  }.elsewhen(setupTile & io.rRawZero) {
    state := State.waitSync
  }.elsewhen((setupTile | waitSync) & ~io.rRawZero) {
    state := State.valid
  }.elsewhen(valid & io.tileAccepted) {
    when(outputHCountLast) {
      state := State.idle
    }.otherwise {
      state := State.updateCounters
    }
  }

  io.tileStartAddress := tileAddress
  io.tileHeight := tileHeight
  io.tileWidth := tileWidth
  io.tileRowToRowDistance := tileRowToRowDistance
  io.tileFirst := tileFirst
  io.tileValid := valid
  io.rRawDec := syncDecRRaw
  io.statusReady := idle
}

object RDmaTileGenerator {
  def main(args: Array[String]): Unit = {
    println(Util.getVerilog(new RDmaTileGenerator(32, 64, 12)))
  }
}
