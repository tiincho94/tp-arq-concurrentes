package iasc.g4.tester;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import iasc.g4.tester.dto.Bid;
import iasc.g4.tester.dto.Buyer;

@Service
public class AuctionService implements ApplicationListener<ServletWebServerInitializedEvent> {

	private static final Logger LOG = LoggerFactory.getLogger(AuctionService.class);

	private static final Random RND = new Random();
	
	private static final int MAX_BID_INCREASE = 10;
	
	private RestTemplate client;
	
	@Value("${auction.server:http://localhost:8081}")
	private String serverHost;
	
	@Value("${client.name:}")
	private String name;

	private String ip;
	
	private Integer port;

	@Value("#{'${client.tags:tag1,tag2}'.split(',')}")
	private List<String> tags;
	
	private final Map<String, Double> auctions;
	
	/**
	 * @throws UnknownHostException
	 */
	public AuctionService() throws UnknownHostException  {
		client = new RestTemplate();
		auctions = new HashMap<>();
	}

	@Override
	public void onApplicationEvent(ServletWebServerInitializedEvent event) {
		this.port = event.getWebServer().getPort();
		try {
			this.ip = InetAddress.getLocalHost().getHostAddress() + ":" + port;
		} catch (UnknownHostException e) {
			LOG.debug("Error obteniendo ip...", e);
			this.ip = "localhost:" + this.port;
		}
		if (null == this.name || this.name.isEmpty()) {
			this.name = "cliente:" + ip;
		}
		registerWithServer();
	}
	
	/**
	 * receive invitation for new auction
	 * @param auctionId
	 */
	public void receiveInvitation(String auctionId) {
		auctions.put(auctionId, 0d);
	}

	/**
	 * update auction price
	 * @param auctionId
	 * @param newPrice
	 */
	public void updatePrice(String auctionId, Double newPrice) {
		auctions.replace(auctionId, newPrice);
	}
	
	/**
	 * forget auction
	 * @param auctionId
	 */
	public void removeAuction(String auctionId) {
		auctions.remove(auctionId);
	}
	
	/**
	 * place bid for all auctions
	 */
	@Scheduled(fixedRateString ="${client.bid.delay:5000}", initialDelay=5000)
	public void bidAll() {
		auctions.forEach( (auctionId, lastPrice) -> {
			bid(auctionId, lastPrice + RND.nextInt(MAX_BID_INCREASE));
		});
	}
	
	/**
	 * place bid
	 * @param auctionId
	 * @param amount
	 */
	private void bid(String auctionId, double amount) {
		LOG.info("Enviando bid a {} de {}", auctionId, amount);
		HttpEntity<Bid> req = new HttpEntity<>(new Bid(auctionId, name, amount));
		ResponseEntity<String> r = client.exchange(serverHost + "/bids", HttpMethod.PUT, req, String.class);
		LOG.info("Resultado de bid a {}: {}", auctionId, r.getBody());
		auctions.replace(auctionId, amount);
	}

	/**
	 * register with server
	 */
	private void registerWithServer() {
		ResponseEntity<String> r = client.postForEntity(serverHost + "/buyers", new Buyer(name, ip, tags), String.class);
		if (r.getBody().toLowerCase().contains("creado")) {
			LOG.info("Registrado como {} en server: {}", name, serverHost);
		} else {
			throw new IllegalStateException("Error registrando en server: " + serverHost);
		}
	}

	/**
	 * @return the serverHost
	 */
	public String getServerHost() {
		return serverHost;
	}

	/**
	 * @return the port
	 */
	public Integer getPort() {
		return port;
	}

	/**
	 * @return the auctions
	 */
	public Map<String, Double> getAuctions() {
		return auctions;
	}
}
