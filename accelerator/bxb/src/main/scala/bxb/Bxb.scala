package bxb

import chisel3._
import chisel3.util._

import bxb.a2f.{A2f}
import bxb.adma.{ADma}
import bxb.array.{MacArray}
import bxb.f2a.{F2a}
import bxb.fdma.{FDma}
import bxb.rdma.{RDma}
import bxb.w2m.{W2m}
import bxb.wqdma.{WDma, QDma}
import bxb.sync.{SemaphorePair, ConsumerSyncMux}
import bxb.memory.{MemArray, PackedBlockRam, TwoBlockMemArray}

// FIXME: not the best way of it
object BxbCsrField {
  // parameter registers
  val start = 0
  val admaInputAddress = 1
  val admaInputHCount = 2
  val admaInputWCount = 3
  val admaInputCCount = 4
  val admaTopTileH = 5
  val admaMiddleTileH = 6
  val admaBottomTileH = 7
  val admaLeftTileW = 8
  val admaMiddleTileW = 9
  val admaRightTileW = 10
  val admaLeftRowToRowDistance = 11
  val admaMiddleRowToRowDistance = 12
  val admaRightRowToRowDistance = 13
  val admaLeftStep = 14
  val admaMiddleStep = 15
  val admaTopRowDistance = 16
  val admaMidRowDistance = 17
  val admaInputSpace = 18
  val admaTopBottomLeftPad = 19
  val admaTopBottomMiddlePad = 20
  val admaTopBottomRightPad = 21
  val admaSidePad = 22
  val wdmaStartAddress = 23
  val wdmaOutputHCount = 24
  val wdmaOutputWCount = 25
  val wdmaKernelBlockCount = 26
  val fdmaOutputAddress = 27
  val fdmaOutputHCount = 28
  val fdmaOutputWCount = 29
  val fdmaOutputCCount = 30
  val fdmaRegularTileH = 31
  val fdmaLastTileH = 32
  val fdmaRegularTileW = 33
  val fdmaLastTileW = 34
  val fdmaRegularRowToRowDistance = 35
  val fdmaLastRowToRowDistance = 36
  val fdmaOutputSpace = 37
  val fdmaRowDistance = 38
  val a2fInputCCount = 39
  val a2fKernelVCount = 40
  val a2fKernelHCount = 41
  val a2fTileStep = 42
  val a2fTileGap = 43
  val a2fOutputHCount = 44
  val a2fOutputWCount = 45
  val a2fRegularTileH = 46
  val a2fLastTileH = 47
  val a2fRegularTileW = 48
  val a2fLastTileW = 49
  val qdmaStartAddress = 50
  val bnqEnable = 51

  val parameterCount = 52

  // status registers
  val statusRegister = parameterCount + 0
}

class BxbCsr(avalonAddrWidth: Int, tileCountWidth: Int) extends Module {
  // FIXME: rid of copypaste
  val hCountWidth = 6
  val wCountWidth = 6
  val blockCountWidth = 14
  val io = IO(new Bundle {
    // Avalon slave interface
    val avalonSlaveAddress = Input(UInt(Chisel.log2Up(50).W)) // FIXME:
    val avalonSlaveWriteData = Input(UInt(32.W))
    val avalonSlaveWrite = Input(Bool())
    val avalonSlaveRead = Input(Bool())
    val avalonSlaveReadData = Output(UInt(32.W))

    // TODO: parameter & start slave
    val start = Output(Bool())

    // ADMA parameters
    val admaInputAddress = Output(UInt(avalonAddrWidth.W))
    val admaInputHCount = Output(UInt(6.W))
    val admaInputWCount = Output(UInt(6.W))
    val admaInputCCount = Output(UInt(6.W))
    val admaOutputCCount = Output(UInt(6.W))
    val admaTopTileH = Output(UInt(tileCountWidth.W))
    val admaMiddleTileH = Output(UInt(tileCountWidth.W))
    val admaBottomTileH = Output(UInt(tileCountWidth.W))
    val admaLeftTileW = Output(UInt(tileCountWidth.W))
    val admaMiddleTileW = Output(UInt(tileCountWidth.W))
    val admaRightTileW = Output(UInt(tileCountWidth.W))
    val admaLeftRowToRowDistance = Output(UInt(tileCountWidth.W))
    val admaMiddleRowToRowDistance = Output(UInt(tileCountWidth.W))
    val admaRightRowToRowDistance = Output(UInt(tileCountWidth.W))
    val admaLeftStep = Output(UInt(avalonAddrWidth.W))
    val admaMiddleStep = Output(UInt(avalonAddrWidth.W))
    val admaTopRowDistance = Output(UInt(avalonAddrWidth.W))
    val admaMidRowDistance = Output(UInt(avalonAddrWidth.W))
    val admaInputSpace = Output(UInt(avalonAddrWidth.W))
    val admaTopBottomLeftPad = Output(UInt(tileCountWidth.W))
    val admaTopBottomMiddlePad = Output(UInt(tileCountWidth.W))
    val admaTopBottomRightPad = Output(UInt(tileCountWidth.W))
    val admaSidePad = Output(UInt(tileCountWidth.W))

    // WDMA parameters
    val wdmaStartAddress = Output(UInt(avalonAddrWidth.W))
    val wdmaOutputHCount = Output(UInt(hCountWidth.W))
    val wdmaOutputWCount = Output(UInt(wCountWidth.W))
    val wdmaKernelBlockCount = Output(UInt(blockCountWidth.W))

    // WDMA parameters
    val qdmaStartAddress = Output(UInt(avalonAddrWidth.W))
    val qdmaOutputHCount = Output(UInt(hCountWidth.W))
    val qdmaOutputWCount = Output(UInt(wCountWidth.W))
    val qdmaKernelBlockCount = Output(UInt(blockCountWidth.W))

    // FDMA parameters
    val fdmaOutputAddress = Output(UInt(avalonAddrWidth.W))
    val fdmaOutputHCount = Output(UInt(6.W))
    val fdmaOutputWCount = Output(UInt(6.W))
    val fdmaOutputCCount = Output(UInt(6.W))
    val fdmaRegularTileH = Output(UInt(tileCountWidth.W))
    val fdmaLastTileH = Output(UInt(tileCountWidth.W))
    val fdmaRegularTileW = Output(UInt(tileCountWidth.W))
    val fdmaLastTileW = Output(UInt(tileCountWidth.W))
    val fdmaRegularRowToRowDistance = Output(UInt(tileCountWidth.W))
    val fdmaLastRowToRowDistance = Output(UInt(tileCountWidth.W))
    val fdmaOutputSpace = Output(UInt(avalonAddrWidth.W))
    val fdmaRowDistance = Output(UInt(avalonAddrWidth.W))

    val rdmaOutputAddress = Output(UInt(avalonAddrWidth.W))
    val rdmaOutputHCount = Output(UInt(6.W))
    val rdmaOutputWCount = Output(UInt(6.W))
    val rdmaOutputCCount = Output(UInt(6.W))
    val rdmaRegularTileH = Output(UInt(tileCountWidth.W))
    val rdmaLastTileH = Output(UInt(tileCountWidth.W))
    val rdmaRegularTileW = Output(UInt(tileCountWidth.W))
    val rdmaLastTileW = Output(UInt(tileCountWidth.W))
    val rdmaRegularRowToRowDistance = Output(UInt(tileCountWidth.W))
    val rdmaLastRowToRowDistance = Output(UInt(tileCountWidth.W))
    val rdmaOutputSpace = Output(UInt(avalonAddrWidth.W))
    val rdmaRowDistance = Output(UInt(avalonAddrWidth.W))

    // A2F parameters
    val a2fInputCCount = Output(UInt(6.W))
    val a2fKernelVCount = Output(UInt(2.W))
    val a2fKernelHCount = Output(UInt(2.W))
    val a2fTileStep = Output(UInt(2.W))
    val a2fTileGap = Output(UInt(2.W))
    val a2fOutputHCount = Output(UInt(6.W))
    val a2fOutputWCount = Output(UInt(6.W))
    val a2fOutputCCount = Output(UInt(6.W))
    val a2fRegularTileH = Output(UInt(tileCountWidth.W))
    val a2fLastTileH = Output(UInt(tileCountWidth.W))
    val a2fRegularTileW = Output(UInt(tileCountWidth.W))
    val a2fLastTileW = Output(UInt(tileCountWidth.W))

    // F2A parameters
    val f2rOutputHCount = Output(UInt(6.W))
    val f2rOutputWCount = Output(UInt(6.W))
    val f2rOutputCCount = Output(UInt(6.W))
    val f2rRegularTileH = Output(UInt(tileCountWidth.W))
    val f2rLastTileH = Output(UInt(tileCountWidth.W))
    val f2rRegularTileW = Output(UInt(tileCountWidth.W))
    val f2rLastTileW = Output(UInt(tileCountWidth.W))

    val bnqEnable = Output(Bool())

    // Modules Status
    val a2fStatusReady = Input(Bool())
    val admaStatusReady = Input(Bool())
    val wdmaStatusReady = Input(Bool())
    val fdmaStatusReady = Input(Bool())
    val qdmaStatusReady = Input(Bool())
    val f2rStatusReady = Input(Bool())
    val rdmaStatusReady = Input(Bool())
  })

  val field = RegInit(VecInit(Seq.fill(BxbCsrField.parameterCount){0.U(32.W)}))
  when(io.avalonSlaveWrite & (io.avalonSlaveAddress =/= BxbCsrField.start.U)) {
    field(io.avalonSlaveAddress) := io.avalonSlaveWriteData
  }

  val readData = Reg(UInt(32.W))
  val status = Cat(0.U(25.W),
    /* 6 */ io.rdmaStatusReady,
    /* 5 */ io.f2rStatusReady,
    /* 4 */ io.qdmaStatusReady,
    /* 3 */ io.a2fStatusReady,
    /* 2 */ io.admaStatusReady,
    /* 1 */ io.wdmaStatusReady,
    /* 0 */ io.fdmaStatusReady
  )
  when(io.avalonSlaveRead & (io.avalonSlaveAddress =/= BxbCsrField.statusRegister.U)) {
    readData := field(io.avalonSlaveAddress)
  }.otherwise {
    readData := status
  }
  io.avalonSlaveReadData := readData

  io.start := RegNext(io.avalonSlaveWrite & (io.avalonSlaveAddress === BxbCsrField.start.U))
  // ADMA
  io.admaInputAddress := field(BxbCsrField.admaInputAddress.U)
  io.admaInputHCount := field(BxbCsrField.admaInputHCount.U)
  io.admaInputWCount := field(BxbCsrField.admaInputWCount.U)
  io.admaInputCCount := field(BxbCsrField.admaInputCCount.U)
  io.admaOutputCCount := field(BxbCsrField.fdmaOutputCCount.U)
  io.admaTopTileH := field(BxbCsrField.admaTopTileH.U)
  io.admaMiddleTileH := field(BxbCsrField.admaMiddleTileH.U)
  io.admaBottomTileH := field(BxbCsrField.admaBottomTileH.U)
  io.admaLeftTileW := field(BxbCsrField.admaLeftTileW.U)
  io.admaMiddleTileW := field(BxbCsrField.admaMiddleTileW.U)
  io.admaRightTileW := field(BxbCsrField.admaRightTileW.U)
  io.admaLeftRowToRowDistance := field(BxbCsrField.admaLeftRowToRowDistance.U)
  io.admaMiddleRowToRowDistance := field(BxbCsrField.admaMiddleRowToRowDistance.U)
  io.admaRightRowToRowDistance := field(BxbCsrField.admaRightRowToRowDistance.U)
  io.admaLeftStep := field(BxbCsrField.admaLeftStep.U)
  io.admaMiddleStep := field(BxbCsrField.admaMiddleStep.U)
  io.admaTopRowDistance := field(BxbCsrField.admaTopRowDistance.U)
  io.admaMidRowDistance := field(BxbCsrField.admaMidRowDistance.U)
  io.admaInputSpace := field(BxbCsrField.admaInputSpace.U)
  io.admaTopBottomLeftPad := field(BxbCsrField.admaTopBottomLeftPad.U)
  io.admaTopBottomMiddlePad := field(BxbCsrField.admaTopBottomMiddlePad.U)
  io.admaTopBottomRightPad := field(BxbCsrField.admaTopBottomRightPad.U)
  io.admaSidePad := field(BxbCsrField.admaSidePad.U)
  // WDMA
  io.wdmaStartAddress := field(BxbCsrField.wdmaStartAddress.U)
  io.wdmaOutputHCount := field(BxbCsrField.wdmaOutputHCount.U)
  io.wdmaOutputWCount := field(BxbCsrField.wdmaOutputWCount.U)
  io.wdmaKernelBlockCount := field(BxbCsrField.wdmaKernelBlockCount.U)
  // QDMA
  io.qdmaStartAddress := field(BxbCsrField.qdmaStartAddress.U)
  io.qdmaOutputHCount := field(BxbCsrField.wdmaOutputHCount.U)
  io.qdmaOutputWCount := field(BxbCsrField.wdmaOutputWCount.U)
  io.qdmaKernelBlockCount := field(BxbCsrField.fdmaOutputCCount.U)
  // FDMA
  io.fdmaOutputAddress := field(BxbCsrField.fdmaOutputAddress.U)
  io.fdmaOutputHCount := field(BxbCsrField.fdmaOutputHCount.U)
  io.fdmaOutputWCount := field(BxbCsrField.fdmaOutputWCount.U)
  io.fdmaOutputCCount := field(BxbCsrField.fdmaOutputCCount.U)
  io.fdmaRegularTileH := field(BxbCsrField.fdmaRegularTileH.U)
  io.fdmaLastTileH := field(BxbCsrField.fdmaLastTileH.U)
  io.fdmaRegularTileW := field(BxbCsrField.fdmaRegularTileW.U)
  io.fdmaLastTileW := field(BxbCsrField.fdmaLastTileW.U)
  io.fdmaRegularRowToRowDistance := field(BxbCsrField.fdmaRegularRowToRowDistance.U)
  io.fdmaLastRowToRowDistance := field(BxbCsrField.fdmaLastRowToRowDistance.U)
  io.fdmaOutputSpace := field(BxbCsrField.fdmaOutputSpace.U)
  io.fdmaRowDistance := field(BxbCsrField.fdmaRowDistance.U)
  // RDMA
  io.rdmaOutputAddress := field(BxbCsrField.fdmaOutputAddress.U)
  io.rdmaOutputHCount := field(BxbCsrField.fdmaOutputHCount.U)
  io.rdmaOutputWCount := field(BxbCsrField.fdmaOutputWCount.U)
  io.rdmaOutputCCount := field(BxbCsrField.fdmaOutputCCount.U)
  io.rdmaRegularTileH := field(BxbCsrField.fdmaRegularTileH.U)
  io.rdmaLastTileH := field(BxbCsrField.fdmaLastTileH.U)
  io.rdmaRegularTileW := field(BxbCsrField.fdmaRegularTileW.U)
  io.rdmaLastTileW := field(BxbCsrField.fdmaLastTileW.U)
  io.rdmaRegularRowToRowDistance := field(BxbCsrField.fdmaRegularRowToRowDistance.U)
  io.rdmaLastRowToRowDistance := field(BxbCsrField.fdmaLastRowToRowDistance.U)
  io.rdmaOutputSpace := field(BxbCsrField.fdmaOutputSpace.U)
  io.rdmaRowDistance := field(BxbCsrField.fdmaRowDistance.U)
  // A2F
  io.a2fInputCCount := field(BxbCsrField.a2fInputCCount.U)
  io.a2fKernelVCount := field(BxbCsrField.a2fKernelVCount.U)
  io.a2fKernelHCount := field(BxbCsrField.a2fKernelHCount.U)
  io.a2fTileStep := field(BxbCsrField.a2fTileStep.U)
  io.a2fTileGap := field(BxbCsrField.a2fTileGap.U)
  io.a2fOutputHCount := field(BxbCsrField.a2fOutputHCount.U)
  io.a2fOutputWCount := field(BxbCsrField.a2fOutputWCount.U)
  io.a2fOutputCCount := field(BxbCsrField.fdmaOutputCCount.U)
  io.a2fRegularTileH := field(BxbCsrField.a2fRegularTileH.U)
  io.a2fLastTileH := field(BxbCsrField.a2fLastTileH.U)
  io.a2fRegularTileW := field(BxbCsrField.a2fRegularTileW.U)
  io.a2fLastTileW := field(BxbCsrField.a2fLastTileW.U)
  // F2R
  io.f2rOutputHCount := field(BxbCsrField.a2fOutputHCount.U)
  io.f2rOutputWCount := field(BxbCsrField.a2fOutputWCount.U)
  io.f2rOutputCCount := field(BxbCsrField.fdmaOutputCCount.U)
  io.f2rRegularTileH := field(BxbCsrField.a2fRegularTileH.U)
  io.f2rLastTileH := field(BxbCsrField.a2fLastTileH.U)
  io.f2rRegularTileW := field(BxbCsrField.a2fRegularTileW.U)
  io.f2rLastTileW := field(BxbCsrField.a2fLastTileW.U)
  // BNQ Enable
  io.bnqEnable := field(BxbCsrField.bnqEnable)(0)
}

class Bxb(dataMemSize: Int, wmemSize: Int, qmemSize: Int) extends Module {
  require(isPow2(dataMemSize))
  require(isPow2(wmemSize))

  val b = 32
  val aWidth = 2
  val fWidth = 16
  val wWidth = 1

  val wEntriesCount = wmemSize / b
  val wSemaWidth = Chisel.log2Floor(wEntriesCount) + 1

  val qEntriesCount = qmemSize
  val qSemaWidth = Chisel.log2Floor(qEntriesCount) + 1

  val avalonAddrWidth = 32
  val maxBurst = 32

  val dataAddrWidth = Chisel.log2Ceil(dataMemSize)
  val wAddrWidth = Chisel.log2Ceil(wmemSize)
  val qAddrWidth = Chisel.log2Ceil(qmemSize)
  val tileCountWidth = dataAddrWidth

  // FIXME: rid of copypaste
  val hCountWidth = 6
  val wCountWidth = 6
  val blockCountWidth = 14

  val io = IO(new Bundle {
    // Avalon slave interface
    val csrSlaveAddress = Input(UInt(Chisel.log2Up(50).W)) // FIXME:
    val csrSlaveWriteData = Input(UInt(32.W))
    val csrSlaveWrite = Input(Bool())
    val csrSlaveRead = Input(Bool())
    val csrSlaveReadData = Output(UInt(32.W))

    // ADMA Avalon Interface
    // FIXME: refactor avalon interface
    val admaAvalonAddress = Output(UInt(avalonAddrWidth.W))
    val admaAvalonRead = Output(Bool())
    val admaAvalonBurstCount = Output(UInt(10.W)) // FIXME: select size based on maxBurst
    val admaAvalonWaitRequest = Input(Bool())
    val admaAvalonReadDataValid = Input(Bool())
    val admaAvalonReadData = Input(UInt((b * aWidth).W))

    // WDMA Avalon Interface
    val wdmaAvalonAddress = Output(UInt(avalonAddrWidth.W))
    val wdmaAvalonRead = Output(Bool())
    val wdmaAvalonBurstCount = Output(UInt(10.W))
    val wdmaAvalonWaitRequest = Input(Bool())
    val wdmaAvalonReadDataValid = Input(Bool())
    val wdmaAvalonReadData = Input(UInt((b * wWidth).W))

    // QDMA Avalon Interface
    val qdmaAvalonAddress = Output(UInt(avalonAddrWidth.W))
    val qdmaAvalonRead = Output(Bool())
    val qdmaAvalonBurstCount = Output(UInt(10.W))
    val qdmaAvalonWaitRequest = Input(Bool())
    val qdmaAvalonReadDataValid = Input(Bool())
    val qdmaAvalonReadData = Input(UInt(64.W))

    // FDMA Avalon Interface
    val fdmaAvalonAddress = Output(UInt(avalonAddrWidth.W))
    val fdmaAvalonBurstCount = Output(UInt(10.W))
    val fdmaAvalonWaitRequest = Input(Bool())
    val fdmaAvalonWrite = Output(Bool())
    val fdmaAvalonWriteData = Output(UInt(128.W))

    // RDMA Avalon Interface
    val rdmaAvalonAddress = Output(UInt(avalonAddrWidth.W))
    val rdmaAvalonBurstCount = Output(UInt(10.W))
    val rdmaAvalonWaitRequest = Input(Bool())
    val rdmaAvalonWrite = Output(Bool())
    val rdmaAvalonWriteData = Output(UInt((b * aWidth).W))
  })

  val csr = Module(new BxbCsr(avalonAddrWidth, tileCountWidth))
  csr.io.avalonSlaveAddress := io.csrSlaveAddress
  csr.io.avalonSlaveWrite := io.csrSlaveWrite
  csr.io.avalonSlaveWriteData := io.csrSlaveWriteData
  csr.io.avalonSlaveRead := io.csrSlaveRead
  io.csrSlaveReadData := csr.io.avalonSlaveReadData

  val asema = Module(new SemaphorePair(2, 0, 2))
  val amem = Module(new MemArray(b, dataMemSize, aWidth))

  val wsema = Module(new SemaphorePair(wSemaWidth, 0, wEntriesCount))
  val wmem = Module(new PackedBlockRam(b, wmemSize, wWidth))

  val msema = Module(new SemaphorePair(2, 0, 2))
  val macArray = Module(new MacArray(b, fWidth, aWidth))

  val fsemaConsumerMux = Module(new ConsumerSyncMux)
  fsemaConsumerMux.io.select := csr.io.bnqEnable
  val fsema = Module(new SemaphorePair(2, 0, 2))
  fsema.io.consumer <> fsemaConsumerMux.io.out

  val fmem = Module(new TwoBlockMemArray(b, dataMemSize, fWidth))
  val fdmaRead = Wire(fmem.io.readB.cloneType)
  val f2rRead = Wire(fmem.io.readB.cloneType)
  fmem.io.readB := Mux(csr.io.bnqEnable, f2rRead, fdmaRead) 

  val qsema = Module(new SemaphorePair(qSemaWidth, 0, qEntriesCount))
  val qmem = Module(new PackedBlockRam(b, qmemSize, 40))

  val rsema = Module(new SemaphorePair(2, 0, 2))
  val rmem = Module(new MemArray(b, dataMemSize, aWidth))

  val adma = Module(new ADma(b, dataAddrWidth, avalonAddrWidth, maxBurst))
  adma.io.start := csr.io.start
  // FIXME: refactor sync interface
  adma.io.aWarZero := asema.io.producer.warZero
  asema.io.producer.warDec := adma.io.aWarDec
  asema.io.producer.rawInc := adma.io.aRawInc

  // FIXME: refactor avalon interface
  io.admaAvalonAddress := adma.io.avalonMasterAddress
  io.admaAvalonRead := adma.io.avalonMasterRead
  io.admaAvalonBurstCount := adma.io.avalonMasterBurstCount
  adma.io.avalonMasterWaitRequest := io.admaAvalonWaitRequest
  adma.io.avalonMasterReadDataValid := io.admaAvalonReadDataValid
  adma.io.avalonMasterReadData := io.admaAvalonReadData

  amem.io.write := adma.io.amemWrite

  // FIXME: refactor parameters
  adma.io.inputAddress := csr.io.admaInputAddress
  adma.io.inputHCount := csr.io.admaInputHCount
  adma.io.inputWCount := csr.io.admaInputWCount
  adma.io.inputCCount := csr.io.admaInputCCount
  adma.io.outputCCount := csr.io.admaOutputCCount
  adma.io.topTileH := csr.io.admaTopTileH
  adma.io.middleTileH := csr.io.admaMiddleTileH
  adma.io.bottomTileH := csr.io.admaBottomTileH
  adma.io.leftTileW := csr.io.admaLeftTileW
  adma.io.middleTileW := csr.io.admaMiddleTileW
  adma.io.rightTileW := csr.io.admaRightTileW
  adma.io.leftRowToRowDistance := csr.io.admaLeftRowToRowDistance
  adma.io.middleRowToRowDistance := csr.io.admaMiddleRowToRowDistance
  adma.io.rightRowToRowDistance := csr.io.admaRightRowToRowDistance
  adma.io.leftStep := csr.io.admaLeftStep
  adma.io.middleStep := csr.io.admaMiddleStep
  adma.io.topRowDistance := csr.io.admaTopRowDistance
  adma.io.midRowDistance := csr.io.admaMidRowDistance
  adma.io.inputSpace := csr.io.admaInputSpace
  adma.io.topBottomLeftPad := csr.io.admaTopBottomLeftPad
  adma.io.topBottomMiddlePad := csr.io.admaTopBottomMiddlePad
  adma.io.topBottomRightPad := csr.io.admaTopBottomRightPad
  adma.io.sidePad := csr.io.admaSidePad
  csr.io.admaStatusReady := adma.io.statusReady

  val wdma = Module(new WDma(b, avalonAddrWidth, b * wWidth, wAddrWidth))
  wdma.io.start := csr.io.start
  // FIXME: refactor sync interface
  wdma.io.wWarZero := wsema.io.producer.warZero
  wsema.io.producer.warDec := wdma.io.wWarDec
  wsema.io.producer.rawInc := wdma.io.wRawInc

  // FIXME: refactor avalon interface
  io.wdmaAvalonAddress := wdma.io.avalonMasterAddress
  io.wdmaAvalonRead := wdma.io.avalonMasterRead
  io.wdmaAvalonBurstCount := wdma.io.avalonMasterBurstCount
  wdma.io.avalonMasterWaitRequest := io.wdmaAvalonWaitRequest
  wdma.io.avalonMasterReadDataValid := io.wdmaAvalonReadDataValid
  wdma.io.avalonMasterReadData := io.wdmaAvalonReadData

  // FIXME: refactor parameters
  wdma.io.startAddress := csr.io.wdmaStartAddress
  wdma.io.outputHCount := csr.io.wdmaOutputHCount
  wdma.io.outputWCount := csr.io.wdmaOutputWCount
  wdma.io.kernelBlockCount := csr.io.wdmaKernelBlockCount

  // FIXME: refactor memory interface
  wmem.io.write := wdma.io.wmemWrite
  csr.io.wdmaStatusReady := wdma.io.statusReady

  val fdma = Module(new FDma(b, dataAddrWidth, avalonAddrWidth, 128, maxBurst))
  fdma.io.start := ~csr.io.bnqEnable & csr.io.start
  // FIXME: refactor sync interface
  fdma.io.fRawZero := fsemaConsumerMux.io.b.rawZero
  fsemaConsumerMux.io.b.rawDec := fdma.io.fRawDec
  fsemaConsumerMux.io.b.warInc := fdma.io.fWarInc

  // FIXME: refactor avalon interface
  io.fdmaAvalonAddress := fdma.io.avalonMasterAddress
  io.fdmaAvalonBurstCount := fdma.io.avalonMasterBurstCount
  fdma.io.avalonMasterWaitRequest := io.fdmaAvalonWaitRequest
  io.fdmaAvalonWrite := fdma.io.avalonMasterWrite
  io.fdmaAvalonWriteData := fdma.io.avalonMasterWriteData

  // FIXME: refactor parameters
  fdma.io.outputAddress := csr.io.fdmaOutputAddress
  fdma.io.outputHCount := csr.io.fdmaOutputHCount
  fdma.io.outputWCount := csr.io.fdmaOutputWCount
  fdma.io.outputCCount := csr.io.fdmaOutputCCount
  fdma.io.regularTileH := csr.io.fdmaRegularTileH
  fdma.io.lastTileH := csr.io.fdmaLastTileH
  fdma.io.regularTileW := csr.io.fdmaRegularTileW
  fdma.io.lastTileW := csr.io.fdmaLastTileW
  fdma.io.regularRowToRowDistance := csr.io.fdmaRegularRowToRowDistance
  fdma.io.lastRowToRowDistance := csr.io.fdmaLastRowToRowDistance
  fdma.io.outputSpace := csr.io.fdmaOutputSpace
  fdma.io.rowDistance := csr.io.fdmaRowDistance

  // FIXME: refactor memory interface
  fdmaRead := fdma.io.fmemRead
  fdma.io.fmemQ := fmem.io.qB
  csr.io.fdmaStatusReady := fdma.io.statusReady

  val rdma = Module(new RDma(b, dataAddrWidth, avalonAddrWidth, maxBurst))
  rdma.io.start := csr.io.bnqEnable & csr.io.start
  // FIXME: refactor sync interface
  rdma.io.rRawZero := rsema.io.consumer.rawZero
  rsema.io.consumer.rawDec := rdma.io.rRawDec
  rsema.io.consumer.warInc := rdma.io.rWarInc

  // FIXME: refactor avalon interface
  io.rdmaAvalonAddress := rdma.io.avalonMasterAddress
  io.rdmaAvalonBurstCount := rdma.io.avalonMasterBurstCount
  rdma.io.avalonMasterWaitRequest := io.rdmaAvalonWaitRequest
  io.rdmaAvalonWrite := rdma.io.avalonMasterWrite
  io.rdmaAvalonWriteData := rdma.io.avalonMasterWriteData

  // FIXME: refactor parameters
  rdma.io.outputAddress := csr.io.rdmaOutputAddress
  rdma.io.outputHCount := csr.io.rdmaOutputHCount
  rdma.io.outputWCount := csr.io.rdmaOutputWCount
  rdma.io.outputCCount := csr.io.rdmaOutputCCount
  rdma.io.regularTileH := csr.io.rdmaRegularTileH
  rdma.io.lastTileH := csr.io.rdmaLastTileH
  rdma.io.regularTileW := csr.io.rdmaRegularTileW
  rdma.io.lastTileW := csr.io.rdmaLastTileW
  rdma.io.regularRowToRowDistance := csr.io.rdmaRegularRowToRowDistance
  rdma.io.lastRowToRowDistance := csr.io.rdmaLastRowToRowDistance
  rdma.io.outputSpace := csr.io.rdmaOutputSpace
  rdma.io.rowDistance := csr.io.rdmaRowDistance

  // FIXME: refactor memory interface
  rmem.io.read := rdma.io.rmemRead
  rdma.io.rmemQ := rmem.io.q
  csr.io.rdmaStatusReady := rdma.io.statusReady

  val qdma = Module(new QDma(b, avalonAddrWidth, 64, qAddrWidth))
  qdma.io.start := csr.io.bnqEnable & csr.io.start
  // FIXME: refactor sync interface
  qdma.io.qWarZero := qsema.io.producer.warZero
  qsema.io.producer.warDec := qdma.io.qWarDec
  qsema.io.producer.rawInc := qdma.io.qRawInc

  // FIXME: refactor avalon interface
  io.qdmaAvalonAddress := qdma.io.avalonMasterAddress
  io.qdmaAvalonRead := qdma.io.avalonMasterRead
  io.qdmaAvalonBurstCount := qdma.io.avalonMasterBurstCount
  qdma.io.avalonMasterWaitRequest := io.qdmaAvalonWaitRequest
  qdma.io.avalonMasterReadDataValid := io.qdmaAvalonReadDataValid
  qdma.io.avalonMasterReadData := io.qdmaAvalonReadData

  // FIXME: refactor parameters
  qdma.io.startAddress := csr.io.qdmaStartAddress
  qdma.io.outputHCount := csr.io.qdmaOutputHCount
  qdma.io.outputWCount := csr.io.qdmaOutputWCount
  qdma.io.kernelBlockCount := csr.io.qdmaKernelBlockCount

  qmem.io.write := qdma.io.qmemWrite
  csr.io.qdmaStatusReady := qdma.io.statusReady

  val w2m = Module(new W2m(b, wmemSize))
  wsema.io.consumer <> w2m.io.wSync
  // FIXME: refactor memory interface
  wmem.io.read := w2m.io.wmemRead
  w2m.io.wmemQ := wmem.io.q

  msema.io.producer <> w2m.io.mSync

  macArray.io.mIn := w2m.io.mOut
  macArray.io.mWe := w2m.io.mWe

  val a2f = Module(new A2f(b, dataMemSize, aWidth, fWidth))
  a2f.io.start := csr.io.start

  asema.io.consumer <> a2f.io.aSync
  // FIXME: refactor memory interface
  amem.io.read := a2f.io.amemRead
  a2f.io.amemQ := amem.io.q

  msema.io.consumer <> a2f.io.mSync
  // FIXME: refactor mac interface
  macArray.io.aIn := a2f.io.aOut
  macArray.io.evenOddIn := a2f.io.evenOddOut
  a2f.io.accIn := macArray.io.accOut

  fsema.io.producer <> a2f.io.fSync
  // FIXME: refactor memory interface
  fmem.io.readA := a2f.io.fmemRead
  fmem.io.writeA := a2f.io.fmemWrite
  a2f.io.fmemQ := fmem.io.qA

  // FIXME: refactor parameters
  a2f.io.inputCCount := csr.io.a2fInputCCount
  a2f.io.kernelVCount := csr.io.a2fKernelVCount
  a2f.io.kernelHCount := csr.io.a2fKernelHCount
  a2f.io.tileStep := csr.io.a2fTileStep
  a2f.io.tileGap := csr.io.a2fTileGap
  a2f.io.outputHCount := csr.io.a2fOutputHCount
  a2f.io.outputWCount := csr.io.a2fOutputWCount
  a2f.io.outputCCount := csr.io.a2fOutputCCount
  a2f.io.regularTileH := csr.io.a2fRegularTileH
  a2f.io.lastTileH := csr.io.a2fLastTileH
  a2f.io.regularTileW := csr.io.a2fRegularTileW
  a2f.io.lastTileW := csr.io.a2fLastTileW
  csr.io.a2fStatusReady := a2f.io.statusReady

  val f2r = Module(new F2a(b, dataMemSize, qmemSize, aWidth, fWidth))
  f2r.io.start := csr.io.bnqEnable & csr.io.start

  f2r.io.outputHCount := csr.io.f2rOutputHCount
  f2r.io.outputWCount := csr.io.f2rOutputWCount
  f2r.io.outputCCount := csr.io.f2rOutputCCount
  f2r.io.regularTileH := csr.io.f2rRegularTileH
  f2r.io.lastTileH := csr.io.f2rLastTileH
  f2r.io.regularTileW := csr.io.f2rRegularTileW
  f2r.io.lastTileW := csr.io.f2rLastTileW

  rmem.io.write := f2r.io.amemWrite

  f2rRead := f2r.io.fmemRead
  f2r.io.fmemQ := fmem.io.qB

  qmem.io.read := f2r.io.qmemRead
  f2r.io.qmemQ := qmem.io.q

  rsema.io.producer <> f2r.io.aSync
  qsema.io.consumer <> f2r.io.qSync
  fsemaConsumerMux.io.a <> f2r.io.fSync

  csr.io.f2rStatusReady := f2r.io.statusReady
}

object Bxb {
  def main(args: Array[String]): Unit = {
    chisel3.Driver.execute(args, () => new Bxb(4 * 1024, 1024, 32))
  }
}
