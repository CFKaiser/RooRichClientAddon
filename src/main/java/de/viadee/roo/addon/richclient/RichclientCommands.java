package de.viadee.roo.addon.richclient;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.springframework.roo.model.JavaPackage;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.shell.CliCommand;
import org.springframework.roo.shell.CliOption;
import org.springframework.roo.shell.CommandMarker;

/**
 * RooRichClientAddon Command class. This class defines all commands that can be used for
 * the RichClient add-on.
 * 
 * @author Christian Kaiser
 * @since 1.1.1
 */
@Component // Use these Apache Felix annotations to register your commands class in the Roo container
@Service
public class RichclientCommands implements CommandMarker { // All command types must implement the CommandMarker interface

	/**
	 * Get a reference to the RichclientOperations from the underlying OSGi container
	 */
	@Reference private RichclientOperations operations; 
	
	/**
	 * Triggers the creation of controller classes and additional components for the 
	 * whole domain model.
	 * 
	 * @param JavaPackage javaPackage
	 */
	@CliCommand(value = "richclient controller all", help = "Creates controllers for all entities")
	public void controllerAll(@CliOption(key = "package", mandatory = true) JavaPackage javaPackage) {
		operations.generateComponentsForAllEntities(javaPackage);
	}
	
	/**
	 * Triggers the creation of controller classes and additional components for a
	 * specified entity.
	 * 
	 * @param JavaPackage javaPackage
	 * @param JavaType entity
	 */
	@CliCommand(value = "richclient controller single", help = "Creates a controller for a specified entity")
	public void controllerSingle(@CliOption(key = "package", mandatory = true) JavaPackage javaPackage, JavaType entity) {
		operations.generateComponentsForSingleEntity(javaPackage, entity);
	}
	
	/**
	 * This method registers a command with the Roo shell. It has no command attribute.
	 */
	@CliCommand(value = "richclient setup", help = "Sets up Richclient addon dependencies")
	public void setup() {
		operations.setup();
	}
}