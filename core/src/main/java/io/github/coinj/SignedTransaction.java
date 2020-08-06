package io.github.coinj;

import org.json.JSONObject;

public class SignedTransaction extends PackedTransaction {
    private JSONObject rawTx;

    public SignedTransaction(Transaction transaction, JSONObject rawTx) {
        super(transaction);
        this.rawTx = rawTx;
    }

    public Boolean isSigned() {
        return rawTx == null;
    }

    public JSONObject getRawTx() {
        return rawTx;
    }

    public void setRawTx(JSONObject rawTx) {
        this.rawTx = rawTx;
    }

    @Override
    public String toString() {
        return "SignedTransaction: " + rawTx;
    }
}
