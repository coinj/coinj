import io.github.coinj.Chain;
import io.github.coinj.Network;
import io.github.coinj.chains.BitcoinChain;
import io.github.coinj.chains.EthereumChain;

public class Address {
    public static void main(String[] args) {
        Chain bitcoin = new BitcoinChain(Network.TEST, null, BitcoinChain.Coin.BTC);
        Chain ethereum = new EthereumChain(Network.TEST, null, EthereumChain.Coin.ETH);

        System.out.println(bitcoin.generateKeyPair());
        System.out.println(bitcoin.generateKeyPair("7783f51f3cab49b1cab5952de8c13472ae196581fba89addf145f1b71c42f4a4"));
        System.out.println(ethereum.generateKeyPair("7783f51f3cab49b1cab5952de8c13472ae196581fba89addf145f1b71c42f4a4"));
    }
}
