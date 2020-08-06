package io.github.coinj;

import java.util.HashMap;
import java.util.Map;

public class PackedTransaction extends Transaction {
    private Map<String, Object> extra = new HashMap<>();

    public PackedTransaction(Transaction transaction) {
        super(transaction.getInputs(), transaction.getOutputs(), transaction.getFee(), transaction.getChange());
    }

    public Object getExtra(String key) {
        return extra.get(key);
    }

    public void setExtra(String key, Object value) {
        extra.put(key, value);
    }
}
