package bxb.f2a

import chisel3._
import chisel3.iotesters.{PeekPokeTester, Driver}
import scala.collection._
import scala.math._

import bxb.memory.{ReadPort, WritePort, PackedWritePort, MemArray, TwoBlockMemArray, PackedBlockRam}

class ReferenceQuantize(val b: Int, val tileHeight: Int, val tileWidth: Int,  val fWidth: Int, val aWidth: Int) {
  val inputChannels = b
  val tileLength = tileWidth * tileHeight
  val input = mutable.Seq.fill(tileLength, inputChannels)(scala.util.Random.nextInt(pow(2, 13).toInt))
  val qInputs = mutable.Seq.fill(b,3)(scala.util.Random.nextInt(8192)) // 13bit
  val qInputsSorted = mutable.Seq.fill(inputChannels,3)(0)
  val qSign = mutable.Seq.fill(inputChannels)(scala.util.Random.nextInt(2))
  val aOutputs = mutable.Seq.fill(inputChannels)(0.U(aWidth.W))
  val th = mutable.Seq.fill(inputChannels)(0L)
  for (col <- 0 until b) {
    qInputsSorted(col) = qInputs(col).sorted
    val th2 = qInputsSorted(col)(2)
    val th1 = qInputsSorted(col)(1)
    val th0 = qInputsSorted(col)(0)
    th(col) = (((th2.toLong << 13) << 13) + (th1.toLong << 13) + th0.toLong) + (qSign(col).toLong << 39)
  }
  val output = mutable.Seq.fill(tileLength, inputChannels)(0)
  for (tl <- 0 until tileLength) {
    for (col <- 0 until inputChannels) {
      if (qSign(col) == 1L) {
        if (input(tl)(col) > qInputsSorted(col)(2)) {
          output(tl)(col) = 3
        } else if (input(tl)(col) > qInputsSorted(col)(1)) {
          output(tl)(col) = 2
        } else if (input(tl)(col) > qInputsSorted(col)(0)) {
          output(tl)(col) = 1
        } else {
          output(tl)(col) = 0
        }
      } else {
        if (input(tl)(col) > qInputsSorted(col)(0)) {
          output(tl)(col) = 3
        } else if (input(tl)(col) > qInputsSorted(col)(1)) {
          output(tl)(col) = 2
        } else if (input(tl)(col) > qInputsSorted(col)(2)) {
          output(tl)(col) = 1
        } else {
          output(tl)(col) = 0
        }
      }
    }
  }
}

class TestF2aQuantizeModule(b: Int, memSize: Int, aWidth: Int, fWidth: Int) extends Module {
  val addrWidth = Chisel.log2Up(memSize)
  val io = IO(new Bundle {
    // AMem test interface
    val amemWriteAddr = Input(Vec(b, ReadPort(addrWidth)))
    val aMemQ = Output(Vec(b, UInt(aWidth.W)))
    // FMem test interface
    val fmemWrite = Input(Vec(b, WritePort(addrWidth, fWidth)))
    // QMem test interface
    val qmemWrite = Input(PackedWritePort(addrWidth, b, 40))
    // Sequencer interface
    val tileHeight = Input(UInt(fWidth.W))
    val tileWidth = Input(UInt(fWidth.W))
    val tileFirst = Input(Bool())
    val tileValid = Input(Bool())

    // Sequencer Sync interface
    val fRawZero = Input(Bool())
    val qRawZero = Input(Bool())
    val aWarZero = Input(Bool())
  })
  val amem = Module(new MemArray(b, memSize, aWidth))
  val fmem = Module (new TwoBlockMemArray(b, memSize, fWidth))
  val qmem = Module (new PackedBlockRam(b, memSize, 40))
  val f2aSequencer = Module(new F2aSequencer(b, addrWidth, addrWidth, addrWidth))
  f2aSequencer.io.hCount := io.tileHeight
  f2aSequencer.io.wCount := io.tileWidth
  f2aSequencer.io.tileFirst := io.tileFirst
  f2aSequencer.io.tileValid := io.tileValid
  f2aSequencer.io.aWarZero := io.aWarZero
  f2aSequencer.io.fRawZero := io.fRawZero
  f2aSequencer.io.qRawZero := io.qRawZero

  val f2aPipeline = Module(new F2aPipeline(b, fWidth, aWidth, addrWidth, addrWidth, addrWidth))
  f2aPipeline.io.control := f2aSequencer.io.control
  fmem.io.readB := f2aPipeline.io.fmemRead
  f2aPipeline.io.fmemQ := fmem.io.qB
  qmem.io.read := f2aPipeline.io.qmemRead
  f2aPipeline.io.qmemQ := qmem.io.q

  fmem.io.writeA := io.fmemWrite
  qmem.io.write := io.qmemWrite
  amem.io.read := io.amemWriteAddr
  amem.io.write := f2aPipeline.io.amemWrite
  io.aMemQ := amem.io.q
  for (col <- 0 until b) {
    fmem.io.readA(col).addr := 0.U
    fmem.io.readA(col).enable := false.B
  }
}

class F2aPipelineQuantizeTests(dut: TestF2aQuantizeModule, b: Int, tileHeight: Int, tileWidth: Int, fWidth: Int, aWidth: Int) extends PeekPokeTester(dut) {
  val ref = new ReferenceQuantize(b, tileHeight, tileWidth, fWidth, aWidth)

  def loadDataToFmem() = {
    for (l <- 0 until ref.tileLength) {
      val addr = l
      for (channel <- 0 until b) {
        poke(dut.io.fmemWrite(channel).addr, addr)
        poke(dut.io.fmemWrite(channel).data, ref.input(l)(channel))
        poke(dut.io.fmemWrite(channel).enable, true)
      }
      step(1)
      for (channel <- 0 until b) {
        poke(dut.io.fmemWrite(channel).enable, false)
      }
    }
  }
  def loadDataToQmem() = {
    val addr = 0
    poke(dut.io.qmemWrite.addr, addr)
    poke(dut.io.qmemWrite.enable, true)
    for (channel <- 0 until b) {
      poke(dut.io.qmemWrite.data(channel), ref.th(channel))
    }
    step(1)
    poke(dut.io.qmemWrite.enable, false)
  }

  poke(dut.io.fRawZero, false)
  poke(dut.io.qRawZero, false)
  poke(dut.io.aWarZero, false)
  poke(dut.io.tileValid, true)
  poke(dut.io.tileFirst, true)

  loadDataToQmem()
  loadDataToFmem()

  poke(dut.io.tileHeight, ref.tileHeight)
  poke(dut.io.tileWidth, ref.tileWidth)

  poke(dut.io.fRawZero, true)
  poke(dut.io.qRawZero, true)
  poke(dut.io.aWarZero, true)

  step(1)

  for (tl <- 0 until ref.tileLength) {
    val addr = tl //TODO: split addr into height and width
    for (channel <- 0 until b) {
      poke(dut.io.amemWriteAddr(channel).addr, addr)
      poke(dut.io.amemWriteAddr(channel).enable, true)
    }
    step(1)
    for (channel <- 0 until b) {
      expect(dut.io.aMemQ(channel), ref.output(tl)(channel))
    }
  }
}

object F2aPipelineQuantizeTests {
  def main(args: Array[String]): Unit = {
    val b = 32
    val fWidth = 16
    val aWidth = 2
    val memSize = 1024
    val tileHeight = 4
    val tileWidth = 3
    val verilatorArgs = Array("--backend-name", "verilator", "--is-verbose", "false")
    val driverArgs = if (args.contains("verilator")) verilatorArgs else Array[String]()
    val ok = Driver.execute(driverArgs, () => new TestF2aQuantizeModule(b, memSize, aWidth, fWidth))(c => new F2aPipelineQuantizeTests(c, b, tileHeight, tileWidth, fWidth, aWidth))
  }
}
