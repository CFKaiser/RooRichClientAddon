package de.viadee.roo.addon.richclient;

import org.springframework.roo.model.JavaType;

/**
 * Interface of commands that are available via the Roo shell.
 * 
 * @author Christian Kaiser
 * @since 1.1.1
 */
public interface SwingOperations {
	
	/**
	 * Triggers the creation of views for all entities.
	 */
	public void createViewsForAllEntities();
	
	/**
	 * Triggers the creation of views for a single entity.
	 */
	public void createViewsForSingleEntity(JavaType entity);

}
