package iasc.g4.tester.dto;

import java.util.List;

public class Buyer {

	private String name;
	private String ip;
	private List<String> tags;
	
	/**
	 * @param name
	 * @param ip
	 * @param tags
	 */
	public Buyer(String name, String ip, List<String> tags) {
		super();
		this.name = name;
		this.ip = ip;
		this.tags = tags;
	}
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}
	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}
	/**
	 * @return the ip
	 */
	public String getIp() {
		return ip;
	}
	/**
	 * @param ip the ip to set
	 */
	public void setIp(String ip) {
		this.ip = ip;
	}
	/**
	 * @return the tags
	 */
	public List<String> getTags() {
		return tags;
	}
	/**
	 * @param tags the tags to set
	 */
	public void setTags(List<String> tags) {
		this.tags = tags;
	}
}
