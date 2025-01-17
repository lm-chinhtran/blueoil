package bxb.f2a

import chisel3._
import chisel3.util._

import bxb.util.{Util}
import bxb.memory.{ReadPort}

class F2aSequencer(b: Int, fAddrWidth: Int, qAddrWidth: Int, aAddrWidth: Int) extends Module {
  val io = IO(new Bundle {
    val control = Output(F2aControl(fAddrWidth, qAddrWidth, aAddrWidth))
    // Q Semaphore Pair Dec interface
    val qRawDec = Output(Bool())
    val qRawZero = Input(Bool())
    // A Semaphore Pair Dec interface
    val aWarDec = Output(Bool())
    val aWarZero = Input(Bool())
    // F Semaphore Pair Dec interface
    val fRawDec = Output(Bool())
    val fRawZero = Input(Bool())

    val hCount = Input(UInt(fAddrWidth.W))
    val wCount = Input(UInt(fAddrWidth.W))

    // Tile generator interface
    val tileFirst = Input(Bool())
    val tileValid = Input(Bool())
    val tileAccepted = Output(Bool())
  })
  object State {
    val idle :: doingQRead :: quantizeFirst :: quantizeRest :: acknowledge :: Nil = Enum(5)
  }

  val state = RegInit(State.idle)
  val idle = (state === State.idle)
  val quantizeFirst = (state === State.quantizeFirst)
  val quantizeRest = (state === State.quantizeRest)
  val doingQuantize = (quantizeFirst | quantizeRest)
  val doingQRead = (state === State.doingQRead)
  val acknowledge = (state === State.acknowledge)

  val syncDecFRaw = RegInit(false.B)
  val syncIncQWar = RegInit(false.B)
  val syncDecAWar = RegInit(false.B)

  val waitRequired = ((doingQRead & io.qRawZero) | (syncDecFRaw & io.fRawZero) | (syncDecAWar & io.aWarZero))

  val wCountLeft = Reg(UInt(fAddrWidth.W))
  val wCountLast = (wCountLeft === 1.U)
  when(~waitRequired) {
    when(doingQRead | wCountLast) {
      wCountLeft := io.wCount
    }.elsewhen(doingQuantize) {
      wCountLeft := wCountLeft - 1.U
    }
  }

  val hCountLeft = Reg(UInt(fAddrWidth.W))
  val hCountLast = (hCountLeft === 1.U) & wCountLast
  when(~waitRequired) {
    when(doingQRead) {
      hCountLeft := io.hCount
    }.elsewhen(doingQuantize & wCountLast) {
      hCountLeft := hCountLeft - 1.U
    }
  }

  val addrMsb = RegInit(0.U(1.W))
  when(~waitRequired) {
    when(idle & io.tileValid & io.tileFirst) {
      addrMsb := 0.U
    }.elsewhen(doingQuantize & hCountLast) {
      addrMsb := ~addrMsb
    }
  }
  val aAddrLow = Reg(UInt((aAddrWidth - 1).W))
  when(~waitRequired) {
    when(idle & io.tileValid) {
      aAddrLow := 0.U
    }.elsewhen(doingQuantize) {
      aAddrLow := aAddrLow + 1.U
    }
  }
  val fAddrLow = Reg(UInt((fAddrWidth - 1).W))
  when(~waitRequired) {
    when(idle & io.tileValid) {
      fAddrLow := 0.U
    }.elsewhen(doingQuantize) {
      fAddrLow := fAddrLow + 1.U
    }
  }

  val qAddr = RegInit(0.U(qAddrWidth.W))
  when(~waitRequired) {
    when(doingQuantize & hCountLast) {
      qAddr := qAddr + 1.U
    }
  }

  syncIncQWar := false.B
  when(~waitRequired) {
    when(doingQRead) {
      syncIncQWar := true.B
    }
  }
  when(~waitRequired) {
    syncDecFRaw := false.B
    when(doingQRead) {
      syncDecFRaw := true.B
    }
  }
  when(~waitRequired) {
    syncDecAWar := false.B
    when(quantizeFirst) {
      syncDecAWar := true.B
    }
  }

  when(~waitRequired) {
    // FIXME: workaround
    when(idle & io.tileValid & ~io.qRawZero) {
      state := State.doingQRead
    }.elsewhen(doingQRead) {
      state := State.quantizeFirst
    }.elsewhen((quantizeFirst | quantizeRest) & hCountLast) {
      state := State.acknowledge
    }.elsewhen(quantizeFirst) {
      state := State.quantizeRest
    }.elsewhen(acknowledge) {
      state := State.idle
    }
  }


  io.control.syncInc.qWar := syncIncQWar
  io.control.syncInc.fWar := acknowledge
  io.control.syncInc.aRaw := acknowledge

  io.aWarDec := syncDecAWar
  io.qRawDec := doingQRead
  io.fRawDec := syncDecFRaw

  io.control.fmemAddr := Cat(addrMsb, fAddrLow)
  io.control.fmemReadEnable := (doingQuantize & ~waitRequired)
  io.control.qmemAddr := qAddr
  io.control.amemAddr := Cat(addrMsb, aAddrLow)
  io.control.amemWriteEnable := (doingQuantize & ~waitRequired)
  io.control.qWe := doingQRead

  io.tileAccepted := acknowledge
}

object F2aSequencer {
  def main(args: Array[String]): Unit = {
    println(Util.getVerilog(new F2aSequencer(10,10,10,10)))
  }
}
