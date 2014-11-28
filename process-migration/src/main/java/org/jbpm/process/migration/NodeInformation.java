package org.jbpm.process.migration;

import java.io.Serializable;

public class NodeInformation implements Serializable {

	private static final long serialVersionUID = 4384427856830159874L;

	private String name;
	private String uniqueId;
	private String nodeId;
	
	public NodeInformation() {
		
	}
	
	public NodeInformation(String name, String uniqueId, String nodeId) {
		this.name = name;
		this.uniqueId = uniqueId;
		this.nodeId = nodeId;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getUniqueId() {
		return uniqueId;
	}
	public void setUniqueId(String uniqueId) {
		this.uniqueId = uniqueId;
	}
	public String getNodeId() {
		return nodeId;
	}
	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}
}
