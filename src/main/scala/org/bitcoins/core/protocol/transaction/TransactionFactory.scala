package org.bitcoins.core.protocol.transaction

import org.bitcoins.core.serializers.transaction.{RawTransactionParser, RawTransactionInputParser}
import org.bitcoins.core.util.Factory

/**
 * Created by chris on 2/21/16.
 */

sealed trait TransactionFactoryHelper
case class UpdateTransactionOutputs(outputs : Seq[TransactionOutput]) extends TransactionFactoryHelper
case class UpdateTransactionInputs(inputs : Seq[TransactionInput]) extends TransactionFactoryHelper
