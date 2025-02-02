package org.bitcoins.core.protocol.dlc.models

import org.bitcoins.core.number.{UInt16, UInt32, UInt64}
import org.bitcoins.core.protocol.script._
import org.bitcoins.core.protocol.tlv.FundingInputV0TLV
import org.bitcoins.core.protocol.transaction._
import org.bitcoins.core.wallet.builder.DualFundingInput
import org.bitcoins.core.wallet.utxo.{InputInfo, ScriptSignatureParams}

sealed trait DLCFundingInput {
  def inputSerialId: UInt64
  def prevTx: Transaction
  def prevTxVout: UInt32
  def sequence: UInt32
  def maxWitnessLen: UInt16
  def redeemScriptOpt: Option[WitnessScriptPubKey]

  def scriptSignature: ScriptSignature = {
    redeemScriptOpt match {
      case Some(redeemScript) => P2SHScriptSignature(redeemScript)
      case None               => EmptyScriptSignature
    }
  }

  lazy val output: TransactionOutput = prevTx.outputs(prevTxVout.toInt)

  lazy val outPoint: TransactionOutPoint =
    TransactionOutPoint(prevTx.txId, prevTxVout)

  lazy val input: TransactionInput = {
    val scriptSig = redeemScriptOpt match {
      case Some(redeemScript) => P2SHScriptSignature(redeemScript)
      case None               => EmptyScriptSignature
    }

    TransactionInput(outPoint, scriptSig, sequence)
  }

  lazy val outputReference: OutputReference = OutputReference(outPoint, output)

  lazy val toTLV: FundingInputV0TLV = {
    FundingInputV0TLV(
      inputSerialId,
      prevTx,
      prevTxVout,
      sequence,
      maxWitnessLen,
      redeemScriptOpt
    )
  }

  lazy val toDualFundingInput: DualFundingInput =
    DualFundingInput(scriptSignature, maxWitnessLen.toInt)
}

object DLCFundingInput {

  def apply(
      inputSerialId: UInt64,
      prevTx: Transaction,
      prevTxVout: UInt32,
      sequence: UInt32,
      maxWitnessLen: UInt16,
      redeemScriptOpt: Option[WitnessScriptPubKey]): DLCFundingInput = {
    prevTx.outputs(prevTxVout.toInt).scriptPubKey match {
      case _: P2SHScriptPubKey =>
        redeemScriptOpt match {
          case Some(redeemScript) =>
            redeemScript match {
              case _: P2WPKHWitnessSPKV0 =>
                require(
                  maxWitnessLen == UInt16(107) || maxWitnessLen == UInt16(108),
                  s"P2WPKH max witness length must be 107 or 108, got $maxWitnessLen")
              case _: P2WSHWitnessSPKV0 => ()
              case spk: WitnessScriptPubKeyV1 =>
                throw new IllegalArgumentException(
                  s"Taproot not yet supported: $spk")
              case spk: UnassignedWitnessScriptPubKey =>
                throw new IllegalArgumentException(
                  s"Unknown segwit version: $spk")
            }

            DLCFundingInputP2SHSegwit(inputSerialId,
                                      prevTx,
                                      prevTxVout,
                                      sequence,
                                      maxWitnessLen,
                                      redeemScript)
          case None =>
            throw new IllegalArgumentException(
              "P2SH input requires a redeem script")
        }
      case _: P2WPKHWitnessSPKV0 =>
        require(
          maxWitnessLen == UInt16(107) || maxWitnessLen == UInt16(108),
          s"P2WPKH max witness length must be 107 or 108, got $maxWitnessLen")
        DLCFundingInputP2WPKHV0(inputSerialId, prevTx, prevTxVout, sequence)
      case _: P2WSHWitnessSPKV0 =>
        DLCFundingInputP2WSHV0(inputSerialId,
                               prevTx,
                               prevTxVout,
                               sequence,
                               maxWitnessLen)
      case spk: WitnessScriptPubKeyV1 =>
        throw new IllegalArgumentException(s"Taproot not yet supported: $spk")
      case spk: UnassignedWitnessScriptPubKey =>
        throw new IllegalArgumentException(s"Unknown segwit version: $spk")
      case spk: RawScriptPubKey =>
        throw new IllegalArgumentException(s"Segwit input required: $spk")
    }
  }

  def fromTLV(fundingInput: FundingInputV0TLV): DLCFundingInput = {
    DLCFundingInput(
      fundingInput.inputSerialId,
      fundingInput.prevTx,
      fundingInput.prevTxVout,
      fundingInput.sequence,
      fundingInput.maxWitnessLen,
      fundingInput.redeemScriptOpt
    )
  }

  def fromInputSigningInfo(
      info: ScriptSignatureParams[InputInfo],
      inputSerialId: UInt64,
      sequence: UInt32): DLCFundingInput = {
    DLCFundingInput(
      inputSerialId,
      info.prevTransaction,
      info.outPoint.vout,
      sequence,
      maxWitnessLen = UInt16(info.maxWitnessLen),
      InputInfo
        .getRedeemScript(info.inputInfo)
        .asInstanceOf[Option[WitnessScriptPubKey]]
    )
  }
}

case class DLCFundingInputP2WPKHV0(
    inputSerialId: UInt64,
    prevTx: Transaction,
    prevTxVout: UInt32,
    sequence: UInt32)
    extends DLCFundingInput {
  require(output.scriptPubKey.isInstanceOf[P2WPKHWitnessSPKV0],
          s"Funding input not P2WPKH: ${output.scriptPubKey}")

  override val maxWitnessLen: UInt16 = UInt16(107)
  override val redeemScriptOpt: Option[WitnessScriptPubKey] = None
}

case class DLCFundingInputP2WSHV0(
    inputSerialId: UInt64,
    prevTx: Transaction,
    prevTxVout: UInt32,
    sequence: UInt32,
    maxWitnessLen: UInt16)
    extends DLCFundingInput {
  require(output.scriptPubKey.isInstanceOf[P2WSHWitnessSPKV0],
          s"Funding input not P2WSH: ${output.scriptPubKey}")

  override val redeemScriptOpt: Option[WitnessScriptPubKey] = None
}

case class DLCFundingInputP2SHSegwit(
    inputSerialId: UInt64,
    prevTx: Transaction,
    prevTxVout: UInt32,
    sequence: UInt32,
    maxWitnessLen: UInt16,
    redeemScript: WitnessScriptPubKey)
    extends DLCFundingInput {
  require(
    output.scriptPubKey == P2SHScriptPubKey(redeemScript),
    s"Funding input not correct P2SH: ${output.scriptPubKey}; expected ${P2SHScriptPubKey(redeemScript)}"
  )

  override val redeemScriptOpt: Option[WitnessScriptPubKey] = Some(redeemScript)
}

case class SpendingInfoWithSerialId(
    spendingInfo: ScriptSignatureParams[InputInfo],
    serialId: UInt64) {

  def toDLCFundingInput(sequence: UInt32): DLCFundingInput =
    DLCFundingInput.fromInputSigningInfo(spendingInfo, serialId, sequence)
}
