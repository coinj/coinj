package io.github.coinj;

import java.io.IOException;
import java.util.List;

public interface Chain {
    PackedTransaction packTransaction(Transaction transaction) throws IOException;
    SignedTransaction signTransaction(PackedTransaction transaction, List<String> keys);
    String sendTransaction(SignedTransaction transaction) throws IOException;
}