package de.viadee.roo.addon.richclient;

import org.springframework.roo.support.style.ToStringCreator;
import org.springframework.roo.support.util.Assert;

/**
 * Example of an enum used for tab-completion of properties.
 * 
 * @since 1.1.1
 */
public enum RichclientPropertyName {
	AUSTRALIA("Australia"),
	UNITED_STATES("United States"),
	GERMANY("Germany"),
	NOT_SPECIFIED("None of your business!");
	
	private String propertyName;
	
	private RichclientPropertyName(String propertyName) {
		Assert.hasText(propertyName, "Property name required");
		this.propertyName = propertyName;
	}
	
	public String getPropertyName() {
		return propertyName;
	}

	public String toString() {
		ToStringCreator tsc = new ToStringCreator(this);
		tsc.append("propertyName", propertyName);
		return tsc.toString();
	}
}