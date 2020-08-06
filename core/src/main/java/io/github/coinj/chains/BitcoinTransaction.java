package io.github.coinj.chains;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;

public class BitcoinTransaction extends Transaction {
    public BitcoinTransaction(NetworkParameters params) {
        super(params);
    }
}
