package dev.noverplay.music.client.model;

public enum ServiceType {
	YOUTUBE("yt", "YouTube");

	private final String id;
	private final String label;

	ServiceType(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public String getId() {
		return id;
	}

	public String getLabel() {
		return label;
	}

	public static ServiceType fromId(String value) {
		if (value == null) {
			return YOUTUBE;
		}
		for (ServiceType type : values()) {
			if (type.id.equalsIgnoreCase(value)) {
				return type;
			}
		}
		return YOUTUBE;
	}
}
