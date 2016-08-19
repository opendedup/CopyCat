package com.datish.copycat.events;

import java.io.Serializable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class VolumeEvent implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	long volumeID;
	long volumeTS;
	long internalTS;
	String actionType;
	long sequence;
	transient JsonObject obj;
	String target;
	String jsonStr;
	private static transient final String MFD = "mfileDelete";
	private static transient final String MFU = "mfileWritten";
	private static transient final String DBU = "sfileWritten";
	private static transient final String DBD = "sfileDeleted";
	
	public VolumeEvent(String jsonStr) {
		JsonParser parser = new JsonParser();
		obj = parser.parse(jsonStr).getAsJsonObject();
		this.volumeID = obj.get("volumeid").getAsLong();
		this.volumeTS = obj.get("timestamp").getAsLong();
		this.internalTS = System.currentTimeMillis();
		this.sequence = obj.get("sequence").getAsLong();
		this.actionType = obj.get("actionType").getAsString();
		this.target = obj.get("object").getAsString();
		this.jsonStr = jsonStr;
	}
	
	public String getJsonString() {
		return this.jsonStr;
	}
	
	public String getChangeID() {
		return this.sequence + "-" + this.volumeID;
	}
	
	public boolean isMFDelete() {
		return actionType.equals(MFD);
	}
	
	public boolean isMFUpdate() {
		return actionType.equals(MFU);
	}
	
	public boolean isDBUpdate() {
		return actionType.equals(DBU);
	}
	public boolean isDBDelete() {
		return actionType.equals(DBD);
	}
	
	public long getVolumeID() {
		return this.volumeID;
	}
	
	public long getVolumeTS() {
		return this.volumeTS;
	}
	
	public long getInternalTS() {
		return this.internalTS;
	}
	
	public String getActionType() {
		return this.actionType;
	}
	
	public String getTarget() {
		return this.target;
	}
	
	
}
