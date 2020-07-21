package iasc.g4.tester.dto;

public class Bid {

	private String auctionId;
	private String buyerName;
	private Double price;
	
	/**
	 * @param auctionId
	 * @param buyerName
	 * @param price
	 */
	public Bid(String auctionId, String buyerName, Double price) {
		super();
		this.auctionId = auctionId;
		this.buyerName = buyerName;
		this.price = price;
	}
	/**
	 * @return the auctionId
	 */
	public String getAuctionId() {
		return auctionId;
	}
	/**
	 * @param auctionId the auctionId to set
	 */
	public void setAuctionId(String auctionId) {
		this.auctionId = auctionId;
	}
	/**
	 * @return the buyerName
	 */
	public String getBuyerName() {
		return buyerName;
	}
	/**
	 * @param buyerName the buyerName to set
	 */
	public void setBuyerName(String buyerName) {
		this.buyerName = buyerName;
	}
	/**
	 * @return the price
	 */
	public Double getPrice() {
		return price;
	}
	/**
	 * @param price the price to set
	 */
	public void setPrice(Double price) {
		this.price = price;
	}
}
