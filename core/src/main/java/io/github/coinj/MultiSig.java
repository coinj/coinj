package io.github.coinj;

import java.util.List;

public interface MultiSig {
    String deployMultiSigContract(List<String> keys, int requiredConfirmations);
}
