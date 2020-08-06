import io.github.coinj.*;
import io.github.coinj.chains.BitcoinChain;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public class Wallet {
    private static final String BITCOIN_TEST_URL = "https://api.bitcore.io/api/BTC/testnet";

    public static void main(String[] args) throws IOException {
        Transaction transaction = new Transaction.Builder()
                .from("mjhAYkzNQbvdWAR2CTtP5HRqdr7RhaWE29")
                .to("mg6QezKh6pidbDEXYFpdP7CLiGZ94k3NAz", BigDecimal.valueOf(0.00001))
                .fee(BigDecimal.valueOf(0.0001)) // optional
                .change("mjhAYkzNQbvdWAR2CTtP5HRqdr7RhaWE29") // optional
                .build();
        // BTC
        Chain bitcoin = new BitcoinChain(Network.TEST, BITCOIN_TEST_URL, BitcoinChain.Coin.BTC);
        // Packing...(online)
        PackedTransaction packedTx = bitcoin.packTransaction(transaction);
        // Signing...(offline)
        List<String> keys = Collections.singletonList("7783f51f3cab49b1cab5952de8c13472ae196581fba89addf145f1b71c42f4a4");
        // Sending...(online)
        SignedTransaction signedTx = bitcoin.signTransaction(packedTx, keys);
        String hash = bitcoin.sendTransaction(signedTx);
        System.out.println("Hash: \t" + hash);
    }
}
