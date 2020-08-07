package io.github.coinj.chains;

import com.google.common.base.Preconditions;
import io.github.coinj.*;
import org.bitcoinj.core.ECKey;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.ChainIdLong;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class EthereumChain implements Chain {
    private final static String DEFAULT_URL = "https://api.infura.io/v1/jsonrpc/mainnet";

    private long chainId;
    private Coin coin;
    private String url = DEFAULT_URL;

    public enum Coin {
        ETH, USDT;

        String getAddress() {
            switch (this) {
                case USDT:
                    return "0xdac17f958d2ee523a2206206994597c13d831ec7";
                default:
                    return null;
            }
        }

        int getDecimals() {
            switch (this) {
                case USDT:
                    return 6;
                default:
                    return 18;
            }
        }
    }

    public EthereumChain(Network network, String url, Coin coin) {
        switch (network) {
            case MAIN:
                chainId = ChainIdLong.MAINNET;
            case TEST:
                chainId = ChainIdLong.KOVAN;
        }
        this.url = url;
        this.coin = coin;
    }

    @Override
    public KeyPair generateKeyPair(String secret) {
        ECKey ecKey = ECKey.fromPrivate(ByteUtils.fromHexString(secret));
        String address = Numeric.prependHexPrefix(Keys.getAddress(Sign.publicKeyFromPrivate(ecKey.getPrivKey())));
        return new KeyPair(ecKey.getPrivateKeyAsHex(), address);
    }

    @Override
    public KeyPair generateKeyPair() {
        return generateKeyPair(new ECKey().getPrivateKeyAsHex());
    }

    @Override
    public PackedTransaction packTransaction(Transaction transaction) throws ExecutionException, InterruptedException {
        PackedTransaction packedTx = new PackedTransaction(transaction);
        Web3j web3 = Web3j.build(new HttpService(url));
        BigInteger gasPrice = web3.ethGasPrice().sendAsync().get().getGasPrice();
        Transaction.Input from = transaction.getInputs().get(0);
        BigInteger nonce = web3.ethGetTransactionCount(from.getAddress(), DefaultBlockParameterName.LATEST).sendAsync().get().getTransactionCount();
        BigInteger gasLimit = null;
        Transaction.Output output = packedTx.getOutputs().get(0);
        BigInteger value = null;
        BigInteger transferValue = output.getAmount().movePointRight(this.coin.getDecimals()).toBigInteger();
        String data = "";
        if (this.coin == Coin.ETH) {
            value = transferValue;
            EthEstimateGas estimateGas = web3.ethEstimateGas(new org.web3j.protocol.core.methods.request.Transaction(from.getAddress(), nonce, null, null, output.getAddress(), value, null)).sendAsync().get();
            gasLimit = estimateGas.getAmountUsed();
        } else {
            value = BigInteger.ZERO;
            String amountHex = ByteUtils.toHexString(transferValue.toByteArray());
            String amountPrefix = "00000000000000000000000000000000000000000000000000000000";
            data = "0xa9059cbb000000000000000000000000" + output.getAddress().substring(2) + amountPrefix.substring(amountPrefix.length() - amountHex.length()) + amountHex;
            EthEstimateGas estimateGas = web3.ethEstimateGas(new org.web3j.protocol.core.methods.request.Transaction(from.getAddress(), nonce, null, null, output.getAddress(), value, data)).sendAsync().get();
            gasLimit = estimateGas.getAmountUsed();
        }

        Preconditions.checkArgument(gasLimit.compareTo(BigInteger.valueOf(100000L)) > 0, "Too much gas limit");

        if (packedTx.getFee() == null) {
            BigInteger fee = gasPrice.multiply(gasLimit);
            packedTx.setFee(new BigDecimal(fee).movePointLeft(18));
        }
        packedTx.setExtra("gasPrice", gasPrice);
        packedTx.setExtra("gasLimit", gasLimit);
        packedTx.setExtra("nonce", nonce);
        packedTx.setExtra("data", data);
        return packedTx;
    }

    @Override
    public SignedTransaction signTransaction(PackedTransaction transaction, List<String> keys) {
        BigInteger nonce = (BigInteger) transaction.getExtra("nonce");
        BigInteger gasPrice = (BigInteger) transaction.getExtra("gasPrice");
        BigInteger gasLimit = (BigInteger) transaction.getExtra("gasLimit");
        BigInteger value = (BigInteger) transaction.getExtra("value");
        String data = (String) transaction.getExtra("data");

        Transaction.Output to = transaction.getOutputs().get(0);
        RawTransaction rawTransaction = RawTransaction.createTransaction(
                nonce, gasPrice, gasLimit, to.getAddress(), value, data);

        // sign & send our transaction
        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, Credentials.create(keys.get(0)));
        String hexValue = Numeric.toHexString(signedMessage);
        /*
            {
              "jsonrpc": "2.0",
              "id": 10,
              "method": "eth_sendRawTransaction",
              "params": [
                "<hex>"
              ]
            }
         */
        JSONObject rawTx = new JSONObject();
        rawTx.put("jsonrpc", "2.0");
        rawTx.put("id", 1);
        rawTx.put("method", "eth_sendRawTransaction");
        JSONArray rawTxParams = new JSONArray();
        rawTxParams.put(hexValue);
        rawTx.put("params", rawTxParams);
        rawTx.put("rawTx", rawTx);
        return new SignedTransaction(transaction, rawTx);
    }

    @Override
    public String sendTransaction(SignedTransaction transaction) {
        return null;
    }
}
