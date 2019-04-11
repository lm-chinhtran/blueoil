package bxb.f2a

import scala.collection._

import chisel3._
import chisel3.iotesters.{PeekPokeTester, Driver}

class DummyControl(val start: Boolean, val aAddr: Int, val aWriteEnable: Boolean, val fAddr: Int, val fReadEnable: Boolean, val qAddr: Int, val decFRaw: Boolean, val incFWar: Boolean, val decAWar: Boolean, val incARaw: Boolean, val decQRaw: Boolean, val incQWar: Boolean) {
}

class DummyControlSequencer(tileHeight: Int, tileWidth: Int, amemSize: Int, qmemSize: Int, fmemSize: Int, repeat: Int) {
  val controlSeq = mutable.ArrayBuffer[DummyControl]()
  val aOffsetSeq = mutable.ArrayBuffer[Int]()
  val vCount = tileHeight * tileWidth

  val amemHalf = amemSize / 2
  val fmemHalf = fmemSize / 2

  var qAddr = 0
  var aAddr = 0
  var fAddr = 0

  for (ki <- 0 until repeat) {
    for (i <- 0 until vCount + 3) {
      val decQRaw = (i == 0)
      val incQWar = (i == 1)
      val decFRaw = (i == 1)
      val incFWar = (i == (vCount + 1))
      val decAWar = (i == 2)
      val incARaw = (i == (vCount + 1))
      if (i == (vCount + 1)) {
        qAddr = (qAddr + 1) % qmemSize
        aAddr = if (aAddr <= amemHalf) amemHalf else 0
        fAddr = if (fAddr <= fmemHalf) fmemHalf else 0
      }
      val aWriteEnable = (i > 0 && i < vCount + 1)
      val fReadEnable = (i > 0 && i < vCount + 1)
      controlSeq += new DummyControl(i == 0, aAddr, aWriteEnable, fAddr, fReadEnable, qAddr, decFRaw, incFWar, decAWar, incARaw, decQRaw, incQWar)
      if ((i >= 1) && (i <= vCount - 0)) {
        fAddr += 1
      }
      if ((i >= 1) && (i <= vCount - 0)) {
        aAddr += 1
      }
    }
  }
}

class F2aSequencerTestSequence(dut: F2aSequencer, tileHeight: Int, tileWidth: Int, amemSize: Int, qmemSize: Int, fmemSize: Int) extends PeekPokeTester(dut) {
  poke(dut.io.hCount, tileHeight)
  poke(dut.io.wCount, tileWidth)
  poke(dut.io.aWarZero, false)
  poke(dut.io.fRawZero, false)
  poke(dut.io.qRawZero, false)

  val ref = new DummyControlSequencer(tileHeight, tileWidth, amemSize, qmemSize, fmemSize, 9)
  for (ctl <- ref.controlSeq) {
    step(1)
    expect(dut.io.writeEnable, ctl.aWriteEnable)
    if (ctl.aWriteEnable) {
      expect(dut.io.amemWriteAddr, ctl.aAddr)
    }
    expect(dut.io.fmemReadEnable, ctl.fReadEnable)
    if (ctl.fReadEnable) {
      expect(dut.io.fmemRead, ctl.fAddr)
    }
    expect(dut.io.qmemRead, ctl.qAddr)
    expect(dut.io.control.syncInc.qWar, ctl.incQWar)
    expect(dut.io.control.syncInc.fWar, ctl.incFWar)
    expect(dut.io.control.syncInc.aRaw, ctl.incARaw)
    expect(dut.io.aWarDec, ctl.decAWar)
    expect(dut.io.fRawDec, ctl.decFRaw)
    expect(dut.io.qRawDec, ctl.decQRaw)
  }
}

class F2aSequencerTestAWarZero(dut: F2aSequencer, tileHeight: Int, tileWidth: Int, amemSize: Int, qmemSize: Int, fmemSize: Int) extends PeekPokeTester(dut) {
  poke(dut.io.hCount, tileHeight)
  poke(dut.io.wCount, tileWidth)
  poke(dut.io.aWarZero, false)
  poke(dut.io.fRawZero, false)
  poke(dut.io.qRawZero, false)
  var waitDelay = 3

  val ref = new DummyControlSequencer(tileHeight, tileWidth, amemSize, qmemSize, fmemSize, 9)
  for (ctl <- ref.controlSeq) {
    if (ctl.start) {
      poke(dut.io.aWarZero, true)
      for (j <- 0 until waitDelay) {
        step(1)
      }
      poke(dut.io.aWarZero, false)
    }
    step(1)
    expect(dut.io.writeEnable, ctl.aWriteEnable)
    if (ctl.aWriteEnable) {
      expect(dut.io.amemWriteAddr, ctl.aAddr)
    }
    expect(dut.io.fmemReadEnable, ctl.fReadEnable)
    if (ctl.fReadEnable) {
      expect(dut.io.fmemRead, ctl.fAddr)
    }
    expect(dut.io.qmemRead, ctl.qAddr)
    expect(dut.io.control.syncInc.qWar, ctl.incQWar)
    expect(dut.io.control.syncInc.fWar, ctl.incFWar)
    expect(dut.io.control.syncInc.aRaw, ctl.incARaw)
    expect(dut.io.aWarDec, ctl.decAWar)
    expect(dut.io.fRawDec, ctl.decFRaw)
    expect(dut.io.qRawDec, ctl.decQRaw)
  }
}

class F2aSequencerTestFRawZero(dut: F2aSequencer, tileHeight: Int, tileWidth: Int, amemSize: Int, qmemSize: Int, fmemSize: Int) extends PeekPokeTester(dut) {
  poke(dut.io.hCount, tileHeight)
  poke(dut.io.wCount, tileWidth)
  poke(dut.io.aWarZero, false)
  poke(dut.io.fRawZero, false)
  poke(dut.io.qRawZero, false)
  var waitDelay = 3

  val ref = new DummyControlSequencer(tileHeight, tileWidth, amemSize, qmemSize, fmemSize, 9)
  for (ctl <- ref.controlSeq) {
    if (ctl.start) {
      poke(dut.io.fRawZero, true)
      for (j <- 0 until waitDelay) {
        step(1)
      }
      poke(dut.io.fRawZero, false)
    }
    step(1)
    expect(dut.io.writeEnable, ctl.aWriteEnable)
    if (ctl.aWriteEnable) {
      expect(dut.io.amemWriteAddr, ctl.aAddr)
    }
    expect(dut.io.fmemReadEnable, ctl.fReadEnable)
    if (ctl.fReadEnable) {
      expect(dut.io.fmemRead, ctl.fAddr)
    }
    expect(dut.io.qmemRead, ctl.qAddr)
    expect(dut.io.control.syncInc.qWar, ctl.incQWar)
    expect(dut.io.control.syncInc.fWar, ctl.incFWar)
    expect(dut.io.control.syncInc.aRaw, ctl.incARaw)
    expect(dut.io.aWarDec, ctl.decAWar)
    expect(dut.io.fRawDec, ctl.decFRaw)
    expect(dut.io.qRawDec, ctl.decQRaw)
  }
}

class F2aSequencerTestQRawZero(dut: F2aSequencer, tileHeight: Int, tileWidth: Int, amemSize: Int, qmemSize: Int, fmemSize: Int) extends PeekPokeTester(dut) {
  poke(dut.io.hCount, tileHeight)
  poke(dut.io.wCount, tileWidth)
  poke(dut.io.aWarZero, false)
  poke(dut.io.fRawZero, false)
  poke(dut.io.qRawZero, false)
  var waitDelay = 3

  val ref = new DummyControlSequencer(tileHeight, tileWidth, amemSize, qmemSize, fmemSize, 9)
  for (ctl <- ref.controlSeq) {
    if (ctl.start) {
      poke(dut.io.qRawZero, true)
      for (j <- 0 until waitDelay) {
        step(1)
      }
      poke(dut.io.qRawZero, false)
    }
    step(1)
    expect(dut.io.writeEnable, ctl.aWriteEnable)
    if (ctl.aWriteEnable) {
      expect(dut.io.amemWriteAddr, ctl.aAddr)
    }
    expect(dut.io.fmemReadEnable, ctl.fReadEnable)
    if (ctl.fReadEnable) {
      expect(dut.io.fmemRead, ctl.fAddr)
    }
    expect(dut.io.qmemRead, ctl.qAddr)
    expect(dut.io.control.syncInc.qWar, ctl.incQWar)
    expect(dut.io.control.syncInc.fWar, ctl.incFWar)
    expect(dut.io.control.syncInc.aRaw, ctl.incARaw)
    expect(dut.io.aWarDec, ctl.decAWar)
    expect(dut.io.fRawDec, ctl.decFRaw)
    expect(dut.io.qRawDec, ctl.decQRaw)
  }
}

object F2aSequencerTests {
  def main(args: Array[String]): Unit = {
    val verilatorArgs = Array("--backend-name", "verilator", "--is-verbose", "true")
    val driverArgs = if (args.contains("verilator")) verilatorArgs else Array[String]()
    val tileHeight = 4
    val tileWidth = 2
    val qmemSize = 4
    val qAddrWidth = Chisel.log2Ceil(qmemSize)
    val amemSize = 16
    val aAddrWidth = Chisel.log2Ceil(amemSize)
    val fmemSize = 16
    val fAddrWidth = Chisel.log2Ceil(fmemSize)
    var ok = true
    ok &= Driver.execute(driverArgs, () => new F2aSequencer(3,10,10,10,fAddrWidth,qAddrWidth,aAddrWidth))(
      dut => new F2aSequencerTestSequence(dut, tileHeight, tileWidth, amemSize, qmemSize, fmemSize))
    ok &= Driver.execute(driverArgs, () => new F2aSequencer(3,10,10,10,fAddrWidth,qAddrWidth,aAddrWidth))(
      dut => new F2aSequencerTestAWarZero(dut, tileHeight, tileWidth, amemSize, qmemSize, fmemSize))
    ok &= Driver.execute(driverArgs, () => new F2aSequencer(3,10,10,10,fAddrWidth,qAddrWidth,aAddrWidth))(
      dut => new F2aSequencerTestFRawZero(dut, tileHeight, tileWidth, amemSize, qmemSize, fmemSize))
    ok &= Driver.execute(driverArgs, () => new F2aSequencer(3,10,10,10,fAddrWidth,qAddrWidth,aAddrWidth))(
      dut => new F2aSequencerTestQRawZero(dut, tileHeight, tileWidth, amemSize, qmemSize, fmemSize))
    if (!ok && args.contains("noexit"))
      System.exit(1)
  }
}
