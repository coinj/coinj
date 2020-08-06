package io.github.coinj.chains;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.coinj.Network;
import io.github.coinj.PackedTransaction;
import io.github.coinj.SignedTransaction;
import io.github.coinj.Transaction;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptPattern;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BitcoinChain extends AbstractChain {
    private final static BigDecimal DUST_THRESHOLD = new BigDecimal(2730).movePointLeft(8);
    private final static String DEFAULT_URL = "https://api.bitcore.io/api/BTC/mainnet";

    private NetworkParameters netParams = MainNetParams.get();
    private final Coin coin;
    private String url = DEFAULT_URL;

    public enum Coin {
        BTC, USDT
    }

    public BitcoinChain(Coin coin) {
        this.coin = coin;
    }

    public BitcoinChain(Network network, String url, Coin coin) {
        switch (network) {
            case MAIN:
                netParams = MainNetParams.get();
            case TEST:
                netParams = TestNet3Params.get();
        }
        this.url = url;
        this.coin = coin;
    }

    private BitcoinTransaction toBitcoinTx(Transaction transaction, List<UnspentOutput> unspentOutputs) {
        BigDecimal totalInputAmount = new BigDecimal(0);
        for (UnspentOutput output : unspentOutputs) {
            BigDecimal amount = BigDecimal.valueOf(output.getValue()).movePointLeft(8);
            totalInputAmount = totalInputAmount.add(amount);
        }

        BigDecimal totalOutputAmount = new BigDecimal(0);
        for (Transaction.Output output : transaction.getOutputs()) {
            totalOutputAmount = totalOutputAmount.add(output.getAmount());
        }

        if (totalInputAmount.compareTo(totalOutputAmount) < 1) {
            throw new RuntimeException("INSUFFICIENT FUNDS");
        }

        BigDecimal fee = BigDecimal.ZERO;
        if (transaction.getFee() != null) {
            fee = transaction.getFee();
        }

        BitcoinTransaction bitcoinTx = new BitcoinTransaction(netParams);
        for (UnspentOutput output : unspentOutputs) {
            TransactionOutPoint outPoint = new TransactionOutPoint(netParams, output.getIndex(), Sha256Hash.wrap(output.getTxId()));
            bitcoinTx.addInput(new TransactionInput(netParams, bitcoinTx, ByteUtils.fromHexString(output.getScript()), outPoint, org.bitcoinj.core.Coin.valueOf(output.getValue())));
        }
        for (Transaction.Output output : transaction.getOutputs()) {
            Long satoshi = output.getAmount().movePointRight(8).longValue();
            bitcoinTx.addOutput(org.bitcoinj.core.Coin.valueOf(satoshi), Address.fromString(netParams, output.getAddress()));
        }

        BigDecimal changeAmount = totalInputAmount.subtract(totalOutputAmount.add(fee));
        if (changeAmount.compareTo(DUST_THRESHOLD) > -1) {
            Preconditions.checkNotNull(transaction.getChange(), "Not found change address");
            bitcoinTx.addOutput(org.bitcoinj.core.Coin.valueOf(changeAmount.movePointRight(8).longValue()), Address.fromString(netParams, transaction.getChange()));
        }
        return bitcoinTx;
    }

    private BigDecimal calcFee(Transaction transaction, List<UnspentOutput> unspentOutputs) throws IOException {
        Request request = new Request.Builder()
                .url(this.url + "/fee/1")
                .build();
        Response response = client.newCall(request).execute();
        JSONObject data = new JSONObject(Objects.requireNonNull(response.body()).string());
        BigDecimal feeRate = BigDecimal.valueOf(data.getDouble("feerate"));

        BigDecimal total = new BigDecimal(0);
        for (UnspentOutput output : unspentOutputs) {
            BigDecimal amount = BigDecimal.valueOf(output.getValue()).movePointLeft(8);
            total = total.add(amount);
        }
        BitcoinTransaction bitcoinTx = toBitcoinTx(transaction, unspentOutputs);
        return feeRate.multiply(BigDecimal.valueOf(bitcoinTx.getMessageSizeForPriorityCalc()));
    }

    @Override
    public PackedTransaction packTransaction(Transaction transaction) throws IOException {
        List<UnspentOutput> unspentOutputs = new ArrayList<>();
        PackedTransaction packedTx = new PackedTransaction(transaction);
        for (Transaction.Input input : transaction.getInputs()) {
            Request request = new Request.Builder()
                    .url(this.url + "/address/" + input.getAddress() + "?unspent=true")
                    .build();
            Response response = client.newCall(request).execute();
            Gson gson = new Gson();
            unspentOutputs.addAll(gson.fromJson(Objects.requireNonNull(response.body()).string(), new TypeToken<ArrayList<UnspentOutput>>(){}.getType()));
        }
        packedTx.setExtra("utxo", unspentOutputs);

        if (transaction.getFee() == null) {
            BigDecimal fee = calcFee(transaction, unspentOutputs);
            packedTx.setFee(fee);
        }
        return packedTx;
    }

    private List<ECKey> selectKeys(String address, List<String> keys) {
        List<ECKey> selectedKeys = new ArrayList<>();
        for (String key : keys) {
            ECKey ecKey = ECKey.fromPrivate(ByteUtils.fromHexString(key));
            Address addr = Address.fromString(netParams, address);
            Address keyAddr = Address.fromKey(netParams, ecKey, addr.getOutputScriptType());

            if (addr.toString().equals(keyAddr.toString())) {
                selectedKeys.add(ecKey);
                return selectedKeys;
            }
        }
        return selectedKeys;
    }

    @Override
    public SignedTransaction signTransaction(PackedTransaction transaction, List<String> keys) {
        List<UnspentOutput> unspentOutputs = (List<UnspentOutput>) transaction.getExtra("utxo");
        BitcoinTransaction bitcoinTx = toBitcoinTx(transaction, unspentOutputs);
        for (int i = 0; i < bitcoinTx.getInputs().size(); i++) {
            TransactionInput input = bitcoinTx.getInput(i);
            List<ECKey> ecKeys = selectKeys(unspentOutputs.get(i).getAddress(), keys);
            ECKey ecKey = ecKeys.get(0);
            Sha256Hash hash = bitcoinTx.hashForSignature(i, new Script(input.getScriptBytes()), org.bitcoinj.core.Transaction.SigHash.ALL, false);
            ECKey.ECDSASignature ecSig = ecKey.sign(hash);
            TransactionSignature txSig = new TransactionSignature(ecSig, org.bitcoinj.core.Transaction.SigHash.ALL, false);

            Script scriptPubKey = new Script(input.getScriptBytes());
            if (ScriptPattern.isP2PK(scriptPubKey)) {
                input.setScriptSig(ScriptBuilder.createInputScript(txSig));
            } else {
                if (!ScriptPattern.isP2PKH(scriptPubKey)) {
                    return null;
                }
                input.setScriptSig(ScriptBuilder.createInputScript(txSig, ecKey));
            }
        }
        // {"rawTx":"02....00"}
        JSONObject rawTx = new JSONObject();
        rawTx.put("rawTx", ByteUtils.toHexString(bitcoinTx.bitcoinSerialize()));
        return new SignedTransaction(transaction, rawTx);
    }

    @Override
    public String sendTransaction(SignedTransaction transaction) throws IOException {
        RequestBody body = RequestBody.create(transaction.getRawTx().toString(), JSON);
        Request request = new Request.Builder()
                .url(this.url + "/tx/send")
                .post(body)
                .build();
        Response response = client.newCall(request).execute();
        JSONObject data = new JSONObject(Objects.requireNonNull(response.body()).string());
        return data.getString("txid");
    }
}