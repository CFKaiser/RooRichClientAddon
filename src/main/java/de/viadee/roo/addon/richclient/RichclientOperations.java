package de.viadee.roo.addon.richclient;

import org.springframework.roo.model.JavaPackage;
import org.springframework.roo.model.JavaType;

/**
 * Interface of commands that are available via the Roo shell.
 * 
 * @author Christian Kaiser
 * @since 1.1.1
 */
public interface RichclientOperations {
	
	/**
	 * Places all dependencies for a RichClient project from configuration.xml 
	 * into the project's Maven configuration pom.xml.
	 */
	void setup();
	
	/**
	 * Creates a controller and decorated entities for beans-binding for every entity 
	 * with the @RooEntity annotation. Triggers the creation of a main class and of
	 * all swing components.
	 * 
	 * @param JavaPackage javaPackage
	 * @param JavaType entity
	 */
	void generateComponentsForSingleEntity(JavaPackage javaPackage, JavaType entity);
	
	/**
	 * Creates a controller and decorated entities for beans-binding for a specified entity. 
	 * Triggers the creation of a main class and of all swing components.
	 * 
	 * @param JavaPackage javaPackage
	 */
	void generateComponentsForAllEntities(JavaPackage javaPackage);
}