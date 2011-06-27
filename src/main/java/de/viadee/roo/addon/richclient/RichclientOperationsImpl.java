package de.viadee.roo.addon.richclient;

import java.beans.Introspector;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.springframework.roo.classpath.PhysicalTypeCategory;
import org.springframework.roo.classpath.PhysicalTypeIdentifier;
import org.springframework.roo.classpath.TypeLocationService;
import org.springframework.roo.classpath.TypeManagementService;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetails;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetailsBuilder;
import org.springframework.roo.classpath.details.ConstructorMetadata;
import org.springframework.roo.classpath.details.ConstructorMetadataBuilder;
import org.springframework.roo.classpath.details.FieldMetadata;
import org.springframework.roo.classpath.details.FieldMetadataBuilder;
import org.springframework.roo.classpath.details.ImportMetadata;
import org.springframework.roo.classpath.details.ImportMetadataBuilder;
import org.springframework.roo.classpath.details.MemberFindingUtils;
import org.springframework.roo.classpath.details.MethodMetadata;
import org.springframework.roo.classpath.details.MethodMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.AnnotatedJavaType;
import org.springframework.roo.classpath.details.annotations.AnnotationAttributeValue;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.ClassAttributeValue;
import org.springframework.roo.classpath.itd.InvocableMemberBodyBuilder;
import org.springframework.roo.classpath.scanner.MemberDetails;
import org.springframework.roo.classpath.scanner.MemberDetailsScanner;
import org.springframework.roo.model.DataType;
import org.springframework.roo.model.JavaPackage;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.process.manager.FileManager;
import org.springframework.roo.process.manager.MutableFile;
import org.springframework.roo.project.Dependency;
import org.springframework.roo.project.Path;
import org.springframework.roo.project.PathResolver;
import org.springframework.roo.project.ProjectOperations;
import org.springframework.roo.support.util.Assert;
import org.springframework.roo.support.util.StringUtils;
import org.springframework.roo.support.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Implementation of {@link RichclientOperations} interface.
 * 
 * @author Christian Kaiser
 * @since 1.1.1
 */
@Component
@Service
public class RichclientOperationsImpl implements RichclientOperations {

	private List<Element> dependencies;
	
	public List<Element> getDependencies() {
		return dependencies;
	}
	
	public void setProjectOperations(ProjectOperations projectOperations) {
		this.projectOperations = projectOperations;
	}
	
	/**
	 * Use TypeLocationService to find types which are annotated with a given annotation in the project
	 */
	@Reference private TypeLocationService typeLocationService;
	
	/**
	 * Get a reference to the TypeManagementService from the underlying OSGi container
	 */
	@Reference private TypeManagementService typeManagementService;
	
	/**
	 * Get a reference to the SwingOperations from the underlying OSGi container
	 */
	@Reference private SwingOperations swingOperations;
	
	/**
	 * Get a reference to the MemberDetailsScanner from the underlying OSGi container
	 */
	@Reference private MemberDetailsScanner memberDetailsScanner;

	/**
	 * Get a reference to the FileManager from the underlying OSGi container. Make sure you
	 * are referencing the Roo bundle which contains this service in your add-on pom.xml.
	 * 
	 * Using the Roo file manager instead if java.io.File gives you automatic rollback in case
	 * an Exception is thrown.
	 */
	@Reference private FileManager fileManager;
	
	/**
	 * Get a reference to the ProjectOperations from the underlying OSGi container. Make sure you
	 * are referencing the Roo bundle which contains this service in your add-on pom.xml.
	 */
	@Reference private ProjectOperations projectOperations;
	
	private JavaSymbolName changeSupportFieldName = new JavaSymbolName("changeSupport");
	
	/**
	 * {@inheritDoc}
	 */
	public void setup() {
		Element configuration = XmlUtils.getConfiguration(getClass());
		
		dependencies = XmlUtils.findElements("/configuration/richroo/dependencies/dependency", configuration);
		for (Element dependencyElement : dependencies) {
			projectOperations.addDependency(new Dependency(dependencyElement));
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void generateComponentsForAllEntities(JavaPackage javaPackage){
		Set<ClassOrInterfaceTypeDetails> cids = typeLocationService.findClassesOrInterfaceDetailsWithAnnotation(new JavaType("org.springframework.roo.addon.entity.RooEntity"));
		for (ClassOrInterfaceTypeDetails cid : cids) {
			if (Modifier.isAbstract(cid.getModifier())) {
				continue;
			}
			JavaType entity = cid.getName();
			
			MemberDetails memberDetails = memberDetailsScanner.getMemberDetails(RichclientOperationsImpl.class.getName(), cid);
			createDecoratedEntityClassForEntity(entity, memberDetails);
			
			generateComponentsForSingleEntity(javaPackage, entity);
		}
		createMainClass(); // TODO: create a method that sets main to TRUE
		swingOperations.createViewsForAllEntities();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void generateComponentsForSingleEntity(JavaPackage javaPackage, JavaType entity) {
		// TODO: complete the method so that it can handle the creation of "decorated entities and views"
		
		createControllerClassForEntity(javaPackage, entity);
		createEventHandlingForEntity(entity);

		installEntity(entity);
	}

	/**
	 * Creates a main class
	 */
	private void createMainClass() {
		JavaType main = new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".main.Main");
		String resourceIdentifier = typeLocationService.getPhysicalLocationCanonicalPath(main, Path.SRC_MAIN_JAVA);
		String declaredByMetadataId = PhysicalTypeIdentifier.createIdentifier(main, projectOperations.getPathResolver().getPath(resourceIdentifier));
		ClassOrInterfaceTypeDetailsBuilder typeDetailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(declaredByMetadataId, Modifier.PUBLIC, main, PhysicalTypeCategory.CLASS);
	
		typeDetailsBuilder.addMethod(getMainMethodForMainClass(declaredByMetadataId));
		
		typeManagementService.generateClassFile(typeDetailsBuilder.build());
	}

	/**
	 * Generates a main MethodMetadata for the main class. The main method will be able to load the
	 * Spring Application Context
	 * 
	 * @param String declaredByMetadataId
	 * @return new MethodMetadata
	 */
	private MethodMetadata getMainMethodForMainClass(String declaredByMetadataId) {
		
		// Specify the desired method name
		JavaSymbolName methodName = new JavaSymbolName("main");
		
		// Define method parameter types
		JavaType string = new JavaType("java.lang.String", 1, DataType.TYPE, null, null);
		AnnotatedJavaType parameterType = new AnnotatedJavaType(string, null);
		
		// Create the method body
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("String[] contextPaths = new String[] {\"META-INF/spring/applicationContext.xml\"};" +
				"new ClassPathXmlApplicationContext(contextPaths);");
		
		// Use the MethodMetadataBuilder for easy creation of MethodMetadata
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId);
		methodBuilder.setMethodName(methodName);
		methodBuilder.addParameterType(parameterType);
		methodBuilder.addParameterName(new JavaSymbolName("args"));
		methodBuilder.setModifier(Modifier.PUBLIC);
		methodBuilder.setReturnType(JavaType.VOID_PRIMITIVE);
		methodBuilder.setBodyBuilder(bodyBuilder);
		
		return methodBuilder.build(); // Build and return a MethodMetadata instance
	}

	/**
	 * Creates a decorated entity class for a specified entity with PropertyChangeSupport 
	 * and extended setter methods for beans-binding.
	 * 
	 * @param JavaType entity
	 * @param MemberDetails memberDetails
	 * @return new JavaType
	 */
	private JavaType createDecoratedEntityClassForEntity(JavaType entity, MemberDetails memberDetails){
		
		// Create new JavaType with a fully qualified type name
		JavaType decoratedEntity = new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".domain.Decorated" + entity.getSimpleTypeName());
		
		String resourceIdentifier = typeLocationService.getPhysicalLocationCanonicalPath(decoratedEntity, Path.SRC_MAIN_JAVA);
		String declaredByMetadataId = PhysicalTypeIdentifier.createIdentifier(decoratedEntity, projectOperations.getPathResolver().getPath(resourceIdentifier));
		ClassOrInterfaceTypeDetailsBuilder typeDetailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(declaredByMetadataId, Modifier.PUBLIC, decoratedEntity, PhysicalTypeCategory.CLASS);
	
		// Confirm that memberDetails is not empty
		Assert.notEmpty(MemberFindingUtils.getMethods(memberDetails));
		
		// for every Method in methodMetadata ...
		for(MethodMetadata methodMetadata : MemberFindingUtils.getMethods(memberDetails)){
			
			// ... get the method name, ...
			JavaSymbolName methodName = new JavaSymbolName(methodMetadata.getMethodName().getSymbolName());
			String name = methodName.getSymbolName();

			// ... check if the method is a setter method (exclude setVersion and setId) ...
			if(name.startsWith("set") && (!name.equalsIgnoreCase("setVersion") && !name.equalsIgnoreCase("setId"))){
				// ... and add method
				typeDetailsBuilder.addMethod(getSetterMethodsForDecoratedEntityClass(declaredByMetadataId, methodMetadata, name));
			}
		}
		
		typeDetailsBuilder.addMethod(getAddPropertyChangeListenerMethodForDecoratedEntityClass(declaredByMetadataId));
		typeDetailsBuilder.addMethod(getRemovePropertyChangeListenerMethodForDecoratedEntityClass(declaredByMetadataId));
		typeDetailsBuilder.addField(getChangeSupportFieldForDecoratedEntityClass(declaredByMetadataId));
		typeDetailsBuilder.addExtendsTypes(entity);
		
		typeManagementService.generateClassFile(typeDetailsBuilder.build());
		
		return typeDetailsBuilder.getName();
	}
	
	/**
	 * Generates addPropertyChangeListener MethodMetadata for a decorated entity.
	 * 
	 * @param String declaredByMetadataId
	 * @return new MethodMetadata
	 */
	private MethodMetadata getAddPropertyChangeListenerMethodForDecoratedEntityClass(String declaredByMetadataId) {
		
		// Specify the desired method name
		JavaSymbolName methodName = new JavaSymbolName("addPropertyChangeListener");

		// Define method parameter types
		JavaType propertyChangeType = new JavaType("java.beans.PropertyChangeListener");
		List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
		parameterTypes.add(new AnnotatedJavaType(propertyChangeType, null));
		
		// Define method parameter names
		List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();
		parameterNames.add(new JavaSymbolName("x"));
		
		// Create the method body
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(changeSupportFieldName + ".addPropertyChangeListener(x);");
		
		// Use the MethodMetadataBuilder for easy creation of MethodMetadata
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, parameterTypes, parameterNames, bodyBuilder);
		
		return methodBuilder.build(); // Build and return a MethodMetadata instance
	}
	
	/**
	 * Generates getPropertyChangeListener MethodMetadata for a decorated entity.
	 * 
	 * @param String declaredByMetadataId
	 * @return new MethodMetadata
	 */
	private MethodMetadata getRemovePropertyChangeListenerMethodForDecoratedEntityClass(String declaredByMetadataId) {
		
		
		JavaSymbolName methodName = new JavaSymbolName("removePropertyChangeListener");
		
		// Define method parameter types
		JavaType propertyChangeType = new JavaType("java.beans.PropertyChangeListener");
		List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
		parameterTypes.add(new AnnotatedJavaType(propertyChangeType, null));
		
		// Define method parameter names
		List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();
		parameterNames.add(new JavaSymbolName("x"));
		
		// Create the method body
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(changeSupportFieldName + ".removePropertyChangeListener(x);");
		
		// Use the MethodMetadataBuilder for easy creation of MethodMetadata
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, parameterTypes, parameterNames, bodyBuilder);
		
		return methodBuilder.build(); // Build and return a MethodMetadata instance
	}

	/**
	 * Generates a setter method with a specified name from MethodMetadata for a decorated entity.
	 * 
	 * @param String declaredByMetadataId
	 * @param MethodMetadata methodMetadata
	 * @return new MethodMetadata
	 */
	private MethodMetadata getSetterMethodsForDecoratedEntityClass(String declaredByMetadataId, MethodMetadata methodMetadata, String name){
		
		// Specify the desired method name
		JavaSymbolName methodName = new JavaSymbolName(name);
		
		// Define method annotations
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(new JavaType("java.lang.Override")));
		
		// Define method parameter types
		List<AnnotatedJavaType> parameterTypes = methodMetadata.getParameterTypes();
		
		// Define method parameter names
		List<JavaSymbolName> parameterNames = methodMetadata.getParameterNames();
		
		// Create the method body
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(parameterTypes.get(0).getJavaType().getSimpleTypeName() + " oldValue = super.get" + name.substring(3) + "();");
		bodyBuilder.appendFormalLine("super." + name + "(" + uncapitalize(name.substring(3)) + ");");
		bodyBuilder.appendFormalLine(changeSupportFieldName + ".firePropertyChange(\"" + uncapitalize(name.substring(3)) + "\", oldValue, " + uncapitalize(name.substring(3)) + ");");

		// Use the MethodMetadataBuilder for easy creation of MethodMetadata
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, parameterTypes, parameterNames, bodyBuilder);
		methodBuilder.setAnnotations(annotations);
		
		return methodBuilder.build(); // Build and return a MethodMetadata instance
	}
	
	/**
	 * Generates a changeSupport field for a decorated entity.
	 * 
	 * @param String declaredByMetadataId
	 * @return new FieldMetadata
	 */
	private FieldMetadata getChangeSupportFieldForDecoratedEntityClass(String declaredByMetadataId){
		
		// Define the field type
		JavaType fieldType = new JavaType("com.jgoodies.binding.beans.ExtendedPropertyChangeSupport");
		
		// Create initializer for field
		String fieldInitializer = "new ExtendedPropertyChangeSupport(this)";
		
		// Use the FieldMetadataBuilder for easy creation of FieldMetadata
		FieldMetadataBuilder fieldMetadataBuilder = new FieldMetadataBuilder(declaredByMetadataId, Modifier.PRIVATE, changeSupportFieldName, fieldType, fieldInitializer);
		
		return fieldMetadataBuilder.build(); // Build and return a FieldMetadata instance
	}
	
	/**
	 * Creates a Controller class for a specified entity.
	 * 
	 * @param JavaPackage javaPackage
	 * @param JavaType entity
	 */
	private void createControllerClassForEntity(JavaPackage javaPackage, JavaType entity){
		JavaType controller = new JavaType(javaPackage.getFullyQualifiedPackageName() + "." + entity.getSimpleTypeName() + "Controller");
		JavaType annotationProcessor = new JavaType("org.bushe.swing.event.annotation.AnnotationProcessor");
		
		String resourceIdentifier = typeLocationService.getPhysicalLocationCanonicalPath(controller, Path.SRC_MAIN_JAVA);
		String declaredByMetadataId = PhysicalTypeIdentifier.createIdentifier(controller, projectOperations.getPathResolver().getPath(resourceIdentifier));
		ClassOrInterfaceTypeDetailsBuilder typeDetailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(declaredByMetadataId, Modifier.PUBLIC, controller, PhysicalTypeCategory.CLASS);
		
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(new JavaType("org.springframework.stereotype.Controller")));
		
		typeDetailsBuilder.setAnnotations(annotations);
		
		typeDetailsBuilder.addConstructor(getConstructorForControllerClass(declaredByMetadataId));
		
		typeDetailsBuilder.addMethod(getCreateMethodForControllerClass(entity, declaredByMetadataId));
		typeDetailsBuilder.addMethod(getDeleteMethodForControllerClass(entity, declaredByMetadataId));
		typeDetailsBuilder.addMethod(getUpdateMethodForControllerClass(entity, declaredByMetadataId));
		typeDetailsBuilder.addMethod(getReadMethodForControllerClass(entity, declaredByMetadataId));
		
		typeDetailsBuilder.getRegisteredImports().add(getImportForJavaType(declaredByMetadataId, annotationProcessor));
		
		typeManagementService.generateClassFile(typeDetailsBuilder.build());
		
		// Define the controller within Spring Application Context
		installEntity(controller);
	}
	
	/**
	 * Generates a constructor for a controller.
	 * 
	 * @param String declaredByMetadataId
	 * @return new ConstructorMetadata
	 */
	private ConstructorMetadata getConstructorForControllerClass(String declaredByMetadataId){
		
		// Create the constructor body
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("AnnotationProcessor.process(this);");
		
		// Use the ConstructorMetadataBuilder for easy creation of ConstructorMetadata
		ConstructorMetadataBuilder constructorBuilder = new ConstructorMetadataBuilder(declaredByMetadataId);
		constructorBuilder.setModifier(Modifier.PUBLIC);
		constructorBuilder.setBodyBuilder(bodyBuilder);
		
		return constructorBuilder.build(); // Build and return a ConstructorMetadata instance
	}
	
	/**
	 * Generates a create method for a controller.
	 * 
	 * @param JavaType entity
	 * @param String declaredByMetadataId
	 * @return new MethodMetadata
	 */
	private MethodMetadata getCreateMethodForControllerClass(JavaType entity, String declaredByMetadataId){
		JavaType eventType = new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".events.Create" + entity.getSimpleTypeName() + "Event");
		String eventName = uncapitalize(eventType.getSimpleTypeName());

		// Specify the desired method name
		JavaSymbolName methodName = new JavaSymbolName("create");
		
		// Define method annotations
		List<AnnotationAttributeValue<?>> eventBusAttributes = new ArrayList<AnnotationAttributeValue<?>>();
		eventBusAttributes.add(new ClassAttributeValue(new JavaSymbolName("eventClass"), eventType));
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(new JavaType("org.bushe.swing.event.annotation.EventSubscriber"), eventBusAttributes));

		// Define method parameter types
		List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
		parameterTypes.add(new AnnotatedJavaType(eventType, null));
		
		// Define method parameter names
		List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();
		parameterNames.add(new JavaSymbolName(eventName));

		// Create the method body
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(eventName + ".get" + entity.getSimpleTypeName() + "()." + "persist();");

		// Use the MethodMetadataBuilder for easy creation of MethodMetadata
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, parameterTypes, parameterNames, bodyBuilder);
		methodBuilder.setAnnotations(annotations);

		return methodBuilder.build(); // Build and return a MethodMetadata instance
	}
	
	/**
	 * Generates a delete method for a controller.
	 * 
	 * @param JavaType entity
	 * @param String declaredByMetadataId
	 * @return new MethodMetadata
	 */
	private MethodMetadata getDeleteMethodForControllerClass(JavaType entity, String declaredByMetadataId){
		JavaType eventType = new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".events.Delete" + entity.getSimpleTypeName() + "Event");
		String eventName = uncapitalize(eventType.getSimpleTypeName());

		// Specify the desired method name
		JavaSymbolName methodName = new JavaSymbolName("delete");
		
		// Check if a method with the same signature already exists in the target type
//		MethodMetadata method = methodExists(methodName, new ArrayList<AnnotatedJavaType>());
//		if (method != null) {
//			// If it already exists, just return the method and omit its generation via the ITD
//			return method;
//		}
		
		// Define method annotations
		List<AnnotationAttributeValue<?>> eventBusAttributes = new ArrayList<AnnotationAttributeValue<?>>();
		eventBusAttributes.add(new ClassAttributeValue(new JavaSymbolName("eventClass"), eventType));
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(new JavaType("org.bushe.swing.event.annotation.EventSubscriber"), eventBusAttributes));

		// Define method parameter types
		List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
		parameterTypes.add(new AnnotatedJavaType(eventType, null));
		
		// Define method parameter names
		List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();
		parameterNames.add(new JavaSymbolName(eventName));

		// Create the method body
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(eventName + ".get" + entity.getSimpleTypeName() + "()." + "remove();");

		// Use the MethodMetadataBuilder for easy creation of MethodMetadata
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, parameterTypes, parameterNames, bodyBuilder);
		methodBuilder.setAnnotations(annotations);

		return methodBuilder.build(); // Build and return a MethodMetadata instance
	}
	
	/**
	 * Generates an update method for a controller.
	 * 
	 * @param JavaType entity
	 * @param String declaredByMetadataId
	 * @return new MethodMetadata
	 */
	private MethodMetadata getUpdateMethodForControllerClass(JavaType entity, String declaredByMetadataId){
		JavaType eventType = new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".events.Update" + entity.getSimpleTypeName() + "Event");
		String eventName = uncapitalize(eventType.getSimpleTypeName());

		// Specify the desired method name
		JavaSymbolName methodName = new JavaSymbolName("update");
		
		// Check if a method with the same signature already exists in the target type
//		MethodMetadata method = methodExists(methodName, new ArrayList<AnnotatedJavaType>());
//		if (method != null) {
//			// If it already exists, just return the method and omit its generation via the ITD
//			return method;
//		}
		
		// Define method annotations
		List<AnnotationAttributeValue<?>> eventBusAttributes = new ArrayList<AnnotationAttributeValue<?>>();
		eventBusAttributes.add(new ClassAttributeValue(new JavaSymbolName("eventClass"), eventType));
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(new JavaType("org.bushe.swing.event.annotation.EventSubscriber"), eventBusAttributes));

		// Define method parameter types
		List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
		parameterTypes.add(new AnnotatedJavaType(eventType, null));
		
		// Define method parameter names
		List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();
		parameterNames.add(new JavaSymbolName(eventName));

		// Create the method body
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(eventName + ".get" + entity.getSimpleTypeName() + "()." + "merge();");

		// Use the MethodMetadataBuilder for easy creation of MethodMetadata
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, parameterTypes, parameterNames, bodyBuilder);
		methodBuilder.setAnnotations(annotations);

		return methodBuilder.build(); // Build and return a MethodMetadata instance
	}
	
	/**
	 * Generates a read method for a controller.
	 * 
	 * @param JavaType entity
	 * @param String declaredByMetadataId
	 * @return new MethodMetadata
	 */
	private MethodMetadata getReadMethodForControllerClass(JavaType entity, String declaredByMetadataId){
		
		JavaType eventType = new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".events.Read" + entity.getSimpleTypeName() + "Event");
		String eventName = uncapitalize(eventType.getSimpleTypeName());

		// Specify the desired method name
		JavaSymbolName methodName = new JavaSymbolName("read");
		
		// Check if a method with the same signature already exists in the target type
//		MethodMetadata method = methodExists(methodName, new ArrayList<AnnotatedJavaType>());
//		if (method != null) {
//			// If it already exists, just return the method and omit its generation via the ITD
//			return method;
//		}
		
		// Define method annotations
		List<AnnotationAttributeValue<?>> eventBusAttributes = new ArrayList<AnnotationAttributeValue<?>>();
		eventBusAttributes.add(new ClassAttributeValue(new JavaSymbolName("eventClass"), eventType));
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(new JavaType("org.bushe.swing.event.annotation.EventSubscriber"), eventBusAttributes));

		// Define method parameter types
		List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
		parameterTypes.add(new AnnotatedJavaType(eventType, null));
		
		// Define method parameter names
		List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();
		parameterNames.add(new JavaSymbolName(eventName));

		// Create the method body
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("Long id = (long) 0;");
		bodyBuilder.appendFormalLine(eventName + ".get" + entity.getClass().getSimpleName() + "()." + "find" + entity.getSimpleTypeName() + "(id);");

		// Use the MethodMetadataBuilder for easy creation of MethodMetadata
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, parameterTypes, parameterNames, bodyBuilder);
		methodBuilder.setAnnotations(annotations);

		return methodBuilder.build(); // Build and return a MethodMetadata instance
	}

	/**
	 * Sets up all components for event handling.
	 * Creates a list of JavaTypes for Events and Listeners (one for each CRUD method).
	 * 	
	 * @param JavaType entity
	 */
	private void createEventHandlingForEntity(JavaType entity){
		List<JavaType> events = new ArrayList<JavaType>();
		List<JavaType> listeners = new ArrayList<JavaType>();
		
		events.add(new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".events.Delete" + entity.getSimpleTypeName() + "Event"));
		events.add(new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".events.Update" + entity.getSimpleTypeName() + "Event"));
		events.add(new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".events.Create" + entity.getSimpleTypeName() + "Event"));
		events.add(new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".events.Read" + entity.getSimpleTypeName() + "Event"));
		
		listeners.add(new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".listeners.Delete" + entity.getSimpleTypeName() + "Listener"));
		listeners.add(new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".listeners.Update" + entity.getSimpleTypeName() + "Listener"));
		listeners.add(new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".listeners.Create" + entity.getSimpleTypeName() + "Listener"));
		listeners.add(new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".listeners.Read" + entity.getSimpleTypeName() + "Listener"));
		
		for(JavaType event : events){
			createEventClassForEntity(event, entity);
		}
		
		for (int i = 0; i < listeners.size(); i++) {
			createListenerClassForEntityAndEvent(listeners.get(i), events.get(i), entity);
		}
	}
	
	/**
	 * Creates a specified Event for an entity.
	 * 
	 * @param JavaType event
	 * @param JavaType entity
	 */
	private void createEventClassForEntity(JavaType event, JavaType entity){
		String resourceIdentifier = typeLocationService.getPhysicalLocationCanonicalPath(event, Path.SRC_MAIN_JAVA);
		String declaredByMetadataId = PhysicalTypeIdentifier.createIdentifier(event, projectOperations.getPathResolver().getPath(resourceIdentifier));
		ClassOrInterfaceTypeDetailsBuilder typeDetailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(declaredByMetadataId, Modifier.PUBLIC, event, PhysicalTypeCategory.CLASS);

		typeDetailsBuilder.addField(getFieldForJavaType(declaredByMetadataId, entity));
		typeDetailsBuilder.addConstructor(getConstructorForJavaType(declaredByMetadataId, entity));
		typeDetailsBuilder.addMethod(getEventGetEntityMethod(event, entity));
		
		typeManagementService.generateClassFile(typeDetailsBuilder.build());
		
		// Register the event with Spring Application Context
		installEventBusEvents(event, entity);
	}
	
	/**
	 * Creates a listener class for every entity and every of their CRUD events.
	 * 
	 * @param JavaType listener
	 * @param JavaType event
	 * @param JavaType entity
	 */
	private void createListenerClassForEntityAndEvent(JavaType listener, JavaType event, JavaType entity){
		String resourceIdentifier = typeLocationService.getPhysicalLocationCanonicalPath(listener, Path.SRC_MAIN_JAVA);
		String declaredByMetadataId = PhysicalTypeIdentifier.createIdentifier(listener, projectOperations.getPathResolver().getPath(resourceIdentifier));
		ClassOrInterfaceTypeDetailsBuilder typeDetailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(declaredByMetadataId, Modifier.PUBLIC, listener, PhysicalTypeCategory.CLASS);
		
		JavaType actionListener = new JavaType("java.awt.event.ActionListener");
		JavaType eventBus = new JavaType("org.bushe.swing.event.EventBus");
		typeDetailsBuilder.addImplementsType(actionListener);
		
		typeDetailsBuilder.addMethod(getActionPerformedMethodForListenerClass(listener, event, entity));
		typeDetailsBuilder.addConstructor(getConstructorForJavaType(declaredByMetadataId, entity));
		typeDetailsBuilder.addField(getFieldForJavaType(declaredByMetadataId, entity));
		
		typeDetailsBuilder.getRegisteredImports().add(getImportForJavaType(declaredByMetadataId, eventBus));
		typeDetailsBuilder.getRegisteredImports().add(getImportForJavaType(declaredByMetadataId, event));

		typeManagementService.generateClassFile(typeDetailsBuilder.build());
		
		// Register the listener with Spring Application Context
		installActionListeners(listener, entity);
	}
	
	/**
	 * Generates a field for a specified JavaType.
	 * 
	 * @param String declaredByMetadataId
	 * @param JavaType javaType
	 * @return new FieldMetadata
	 */
	private FieldMetadata getFieldForJavaType(String declaredByMetadataId, JavaType javaType){
		
		// Define the desired field name
		JavaSymbolName fieldName = new JavaSymbolName(uncapitalize(javaType.getSimpleTypeName()));
		
		// Use the FieldMetadataBuilder for easy creation of FieldMetadata
		FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(declaredByMetadataId, Modifier.PRIVATE, fieldName, javaType, null);
		
		return fieldBuilder.build(); // Build and return a FieldMetadata instance
	}
	
	/**
	 * Generates a constructor for a specified JavaType.
	 * 
	 * @param String declaredByMetadataId
	 * @param JavaType javaType
	 * @return new ConstructorMetadata
	 */
	private ConstructorMetadata getConstructorForJavaType(String declaredByMetadataId, JavaType javaType){

		AnnotatedJavaType constructorParamType = new AnnotatedJavaType(javaType, null);
		
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("this." + uncapitalize(javaType.getSimpleTypeName()) + " = " + uncapitalize(javaType.getSimpleTypeName()) + ";");
		
		ConstructorMetadataBuilder constructorBuilder = new ConstructorMetadataBuilder(declaredByMetadataId);
		constructorBuilder.addParameterType(constructorParamType);
		constructorBuilder.addParameterName(new JavaSymbolName(uncapitalize(javaType.getSimpleTypeName())));
		constructorBuilder.setModifier(Modifier.PUBLIC);
		constructorBuilder.setBodyBuilder(bodyBuilder);
		
		return constructorBuilder.build();
	}
	
	/**
	 * Generates an import for a specified JavaType.
	 * 
	 * @param String declaredByMetadataId
	 * @param JavaType javaType
	 * @return new ImportMetadata
	 */
	private ImportMetadata getImportForJavaType(String declaredByMetadataId, JavaType javaType){

		ImportMetadataBuilder importBuilder = new ImportMetadataBuilder(declaredByMetadataId);
		importBuilder.setImportType(javaType);
		
		return importBuilder.build();
	}
	
	/**
	 * Generates an actionPerformed method for the specified listener class.
	 * 
	 * @param JavaType listener
	 * @param JavaType event
	 * @param JavaType entity
	 * @return new MethodMetadata
	 */
	private MethodMetadata getActionPerformedMethodForListenerClass(JavaType listener, JavaType event, JavaType entity){
		String declaredByMetadataId = PhysicalTypeIdentifier.createIdentifier(listener, Path.SRC_MAIN_JAVA);
		
		// Define the desired method name
		JavaSymbolName methodName = new JavaSymbolName("actionPerformed");
		
		// Define method annotations
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(new JavaType("java.lang.Override")));
		
		// Define method parameter types
		JavaType actionEvent = new JavaType("java.awt.event.ActionEvent");
		List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
		parameterTypes.add(new AnnotatedJavaType(actionEvent, null));
		
		// Define method parameter names
		List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();
		parameterNames.add(new JavaSymbolName("e"));
		
		// Create the method body
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("EventBus.publish(new " + event.getSimpleTypeName() + "(" + uncapitalize(entity.getSimpleTypeName()) + "));");
		
		// Use the MethodMetadataBuilder for easy creation of MethodMetadata
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, parameterTypes, parameterNames, bodyBuilder);
		methodBuilder.setAnnotations(annotations);
		
		return methodBuilder.build(); // Build and return a MethodMetadata instance
	}

	/**
	 * Generates a getEvent method for a specified event class.
	 * 
	 * @param JavaType source
	 * @param JavaType entity
	 * @return new MethodMetadata
	 */
	private MethodMetadata getEventGetEntityMethod(JavaType source, JavaType entity){
		String declaredByMetadataId = PhysicalTypeIdentifier.createIdentifier(source, Path.SRC_MAIN_JAVA);
		
		// Define the desired method name
		JavaSymbolName methodName = new JavaSymbolName("get" + entity.getSimpleTypeName());
		
		// Create the method body
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("return " + uncapitalize(entity.getSimpleTypeName()) + ";");
		
		// Use the MethodMetadataBuilder for easy creation of MethodMetadata
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, entity , bodyBuilder);
		
		return methodBuilder.build(); // Build and return a MethodMetadata instance
	}
	

	// --------------- INSTALL COMPONENTS TO APPLICATION CONTEXT ------------------- //
	
	private void installEntity(JavaType javaType){
		Assert.isTrue(projectOperations.isProjectAvailable(), "Project metadata required");
		PathResolver pathResolver = projectOperations.getPathResolver();
		
		// Verify that the application context already exists
		String appContextPath = pathResolver.getIdentifier(Path.SPRING_CONFIG_ROOT, "applicationContext.xml");
		Assert.isTrue(fileManager.exists(appContextPath), "Application context does not exist");

		MutableFile appContextMutableFile = null;
		
		Document appContextXml = null;
		try {
			if (fileManager.exists(appContextPath)) {
				appContextMutableFile = fileManager.updateFile(appContextPath);
				appContextXml = XmlUtils.getDocumentBuilder().parse(appContextMutableFile.getInputStream());
			} else {
				new IllegalStateException("Could not aquire the Spring applicationContext.xml file");
			}
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
		
		Element root = (Element) appContextXml.getFirstChild();
		
		Element bean = XmlUtils.findFirstElement("/beans/bean[@class = '" + javaType.getFullyQualifiedTypeName() + "']", root);
		if (bean != null) {
			root.removeChild(bean);
		}
		
		bean = appContextXml.createElement("bean");
		bean.setAttribute("class", javaType.getFullyQualifiedTypeName());
		bean.setAttribute("id", javaType.getSimpleTypeName());
		
		root.appendChild(bean);
		
		XmlUtils.writeXml(appContextMutableFile.getOutputStream(), appContextXml);
	}
	
	private void installActionListeners(JavaType javaType, JavaType entity){
		String entityName = entity.getSimpleTypeName();
		
		Assert.isTrue(projectOperations.isProjectAvailable(), "Project metadata required");
		
		// Verify that the application context already exists
		PathResolver pathResolver = projectOperations.getPathResolver();
		String appContextPath = pathResolver.getIdentifier(Path.SPRING_CONFIG_ROOT, "applicationContext.xml");
		Assert.isTrue(fileManager.exists(appContextPath), "Application context does not exist");

		MutableFile appContextMutableFile = null;
		
		Document appContextXml = null;
		try {
			if (fileManager.exists(appContextPath)) {
				appContextMutableFile = fileManager.updateFile(appContextPath);
				appContextXml = XmlUtils.getDocumentBuilder().parse(appContextMutableFile.getInputStream());
			} else {
				new IllegalStateException("Could not aquire the Spring applicationContext.xml file");
			}
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
		
		Element root = (Element) appContextXml.getFirstChild();
		
		Element bean = XmlUtils.findFirstElement("/beans/bean[@class = '" + javaType.getFullyQualifiedTypeName() + "']", root);
		if (bean != null) {
			root.removeChild(bean);
		}
		
		bean = appContextXml.createElement("bean");
		bean.setAttribute("class", javaType.getFullyQualifiedTypeName());
		bean.setAttribute("id", javaType.getSimpleTypeName());
		
		Element constructorArg = XmlUtils.findFirstElement("//constructor-arg[@name='"+ uncapitalize(entityName) +"']", bean);
		if (constructorArg != null) {
			bean.removeChild(constructorArg);
		}
		constructorArg = appContextXml.createElement("constructor-arg");
		constructorArg.setAttribute("ref", entityName);
		constructorArg.setAttribute("name", uncapitalize(entityName));
		bean.appendChild(constructorArg);
		
		root.appendChild(bean);
		
		XmlUtils.writeXml(appContextMutableFile.getOutputStream(), appContextXml);
	}
	
	private void installEventBusEvents(JavaType javaType, JavaType entity){
		String entityName = entity.getSimpleTypeName();
		
		Assert.isTrue(projectOperations.isProjectAvailable(), "Project metadata required");
		PathResolver pathResolver = projectOperations.getPathResolver();
		
		// Verify that the application context already exists
		String appContextPath = pathResolver.getIdentifier(Path.SPRING_CONFIG_ROOT, "applicationContext.xml");
		Assert.isTrue(fileManager.exists(appContextPath), "Application context does not exist");

		MutableFile appContextMutableFile = null;
		
		Document appContextXml = null;
		try {
			if (fileManager.exists(appContextPath)) {
				appContextMutableFile = fileManager.updateFile(appContextPath);
				appContextXml = XmlUtils.getDocumentBuilder().parse(appContextMutableFile.getInputStream());
			} else {
				new IllegalStateException("Could not aquire the Spring applicationContext.xml file");
			}
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
		
		Element root = (Element) appContextXml.getFirstChild();
		
		Element bean = XmlUtils.findFirstElement("/beans/bean[@class = '" + javaType.getFullyQualifiedTypeName() + "']", root);
		if (bean != null) {
			root.removeChild(bean);
		}
		
		bean = appContextXml.createElement("bean");
		bean.setAttribute("class", javaType.getFullyQualifiedTypeName());
		bean.setAttribute("id", javaType.getSimpleTypeName());
		
		Element constructorArg = XmlUtils.findFirstElement("//constructor-arg[@name='"+ uncapitalize(entityName) +"']", bean);
		if (constructorArg != null) {
			bean.removeChild(constructorArg);
		}
		constructorArg = appContextXml.createElement("constructor-arg");
		constructorArg.setAttribute("ref", entityName);
		constructorArg.setAttribute("name", uncapitalize(entityName));
		bean.appendChild(constructorArg);
		
		root.appendChild(bean);
		
		XmlUtils.writeXml(appContextMutableFile.getOutputStream(), appContextXml);
	}
	
	private String uncapitalize(String term) {
		// [ROO-1790] this is needed to adhere to the JavaBean naming conventions (see JavaBean spec section 8.8)
		return Introspector.decapitalize(StringUtils.capitalize(term));
	}
		
}