package io.github.coinj.chains;

import io.github.coinj.Chain;
import io.github.coinj.PackedTransaction;
import io.github.coinj.SignedTransaction;
import io.github.coinj.Transaction;

import java.util.List;

public class EthereumChain implements Chain {
    public enum Coin {
        ETH, USDT
    }

    @Override
    public PackedTransaction packTransaction(Transaction transaction) {
        return null;
    }

    @Override
    public SignedTransaction signTransaction(PackedTransaction transaction, List<String> keys) {
        return null;
    }

    @Override
    public String sendTransaction(SignedTransaction transaction) {
        return null;
    }
}
