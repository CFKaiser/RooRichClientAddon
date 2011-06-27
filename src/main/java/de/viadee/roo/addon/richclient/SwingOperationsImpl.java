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
import org.springframework.roo.classpath.details.FieldMetadata;
import org.springframework.roo.classpath.details.FieldMetadataBuilder;
import org.springframework.roo.classpath.details.ImportMetadata;
import org.springframework.roo.classpath.details.ImportMetadataBuilder;
import org.springframework.roo.classpath.details.MethodMetadata;
import org.springframework.roo.classpath.details.MethodMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.AnnotatedJavaType;
import org.springframework.roo.classpath.details.annotations.AnnotationAttributeValue;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.ClassAttributeValue;
import org.springframework.roo.classpath.itd.InvocableMemberBodyBuilder;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.project.Path;
import org.springframework.roo.project.ProjectOperations;
import org.springframework.roo.support.util.StringUtils;

/**
 * Implementation of {@link SwingOperations} interface.
 * 
 * @author Christian Kaiser
 * @since 1.1.1
 */
@Component
@Service
public class SwingOperationsImpl implements SwingOperations{
	
	@Reference private ProjectOperations projectOperations;
	@Reference private TypeLocationService typeLocationService;
	@Reference private TypeManagementService typeManagementService;
	@Reference private RooSwingComponentOperations componentOperations;
	
	/**
	 * {@inheritDoc}
	 */
	public void createViewsForAllEntities(){
		
		componentOperations.createRooComponents();
		
		Set<ClassOrInterfaceTypeDetails> cids = typeLocationService.findClassesOrInterfaceDetailsWithAnnotation(new JavaType("org.springframework.roo.addon.entity.RooEntity"));
		for (ClassOrInterfaceTypeDetails cid : cids) {
			if (Modifier.isAbstract(cid.getModifier())) {
				continue;
			}
			
			JavaType entity = cid.getName();
			
			createViewsForSingleEntity(entity);
		}
//		createWindowManager(cids);
		createMenuListener();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void createViewsForSingleEntity(JavaType entity) {
		// TODO: Implement method
	}
	
	/**
	 * Creates a WindowManager class for the handling of different views
	 * 
	 * @param Set<ClassOrInterfaceTypeDetails> cids
	 */
	private void createWindowManager(Set<ClassOrInterfaceTypeDetails> cids) {
		JavaType windowManager = new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".gui.RooRichWindowManager");
		String resourceIdentifier = typeLocationService.getPhysicalLocationCanonicalPath(windowManager, Path.SRC_MAIN_JAVA);
		String declaredByMetadataId = PhysicalTypeIdentifier.createIdentifier(windowManager, projectOperations.getPathResolver().getPath(resourceIdentifier));
		ClassOrInterfaceTypeDetailsBuilder typeDetailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(declaredByMetadataId, Modifier.PUBLIC, windowManager, PhysicalTypeCategory.CLASS);
		
		typeDetailsBuilder.addMethod(getHandleMenuEventMethod(declaredByMetadataId, cids));
		
		for(ClassOrInterfaceTypeDetails cid : cids){
			typeDetailsBuilder.addField(getListPanelFields(declaredByMetadataId, cid));
		}
		
		typeManagementService.generateClassFile(typeDetailsBuilder.build());
	}
	
	private FieldMetadata getListPanelFields(String declaredByMetadataId, ClassOrInterfaceTypeDetails cid){
		JavaSymbolName fieldName = new JavaSymbolName("list" + cid.getName().getSimpleTypeName() + "Panel");
		JavaType type = new JavaType("com.viadee.richroo.swing.RichRooPanel");
		FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(declaredByMetadataId, Modifier.PRIVATE, fieldName, type, null);
		return fieldBuilder.build();
	}
	
	private MethodMetadata getHandleMenuEventMethod(String declaredByMetadataId, Set<ClassOrInterfaceTypeDetails> cids) {
		JavaType eventType = new JavaType("javax.swing.event.MenuEvent");
		String eventName = uncapitalize(eventType.getSimpleTypeName());
		
		// Specify the desired method name
		JavaSymbolName methodName = new JavaSymbolName("handleMenuEvent");
		
		// Check if a method with the same signature already exists in the target type
//		MethodMetadata method = methodExists(methodName, new ArrayList<AnnotatedJavaType>());
//		if (method != null) {
//			// If it already exists, just return the method and omit its generation via the ITD
//			return method;
//		}
		
		// Define method annotations (none in this case)
		List<AnnotationAttributeValue<?>> eventBusAttributes = new ArrayList<AnnotationAttributeValue<?>>();
		eventBusAttributes.add(new ClassAttributeValue(new JavaSymbolName("eventClass"), eventType));
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(new JavaType("org.bushe.swing.event.annotation.EventSubscriber"), eventBusAttributes));

		// Define method parameter types (none in this case)
		List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
		parameterTypes.add(new AnnotatedJavaType(eventType, null));
		
		// Define method parameter names (none in this case)
		List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();
		parameterNames.add(new JavaSymbolName(eventName));
		
		List<String> entityNames = new ArrayList<String>();
		for (ClassOrInterfaceTypeDetails cid : cids) {
			if (Modifier.isAbstract(cid.getModifier())) {
				continue;
			}
			
			entityNames.add(cid.getName().getSimpleTypeName());
		}

		String exit = "\"Exit\"";

		// Create the method body
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("String source = " + eventName + ".getSource().toString();");
		bodyBuilder.appendFormalLine("if (source.equalsIgnoreCase(" + exit + ")) {");
		bodyBuilder.appendFormalLine("System.exit(0);");
		
		if(entityNames != null){
			for(String entityName : entityNames){
				bodyBuilder.appendFormalLine("} else if (source.equalsIgnoreCase(\"" + entityName + "\")) {");
				bodyBuilder.appendFormalLine("mainFrame.setContentPane(list" + entityName + "Panel);");
			}
		}
		
		bodyBuilder.appendFormalLine("}");
		
		// Use the MethodMetadataBuilder for easy creation of MethodMetadata
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, parameterTypes, parameterNames, bodyBuilder);
		methodBuilder.setAnnotations(annotations);

		return methodBuilder.build(); // Build and return a MethodMetadata instance
	}

	private void createMenuListener(){
		JavaType menuListener = new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".listeners.MenuListener");
		String resourceIdentifier = typeLocationService.getPhysicalLocationCanonicalPath(menuListener, Path.SRC_MAIN_JAVA);
		String declaredByMetadataId = PhysicalTypeIdentifier.createIdentifier(menuListener, projectOperations.getPathResolver().getPath(resourceIdentifier));
		ClassOrInterfaceTypeDetailsBuilder typeDetailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(declaredByMetadataId, Modifier.PUBLIC, menuListener, PhysicalTypeCategory.CLASS);
		
		JavaType actionListener = new JavaType("java.awt.event.ActionListener");
		typeDetailsBuilder.addImplementsType(actionListener);
		
		JavaType eventBus = new JavaType("org.bushe.swing.event.EventBus");
		JavaType menuEvent = new JavaType("javax.swing.event.MenuEvent");
		
		typeDetailsBuilder.addMethod(getMenuActionPerformedMethod(menuEvent, declaredByMetadataId));
		typeDetailsBuilder.getRegisteredImports().add(getImport(declaredByMetadataId, eventBus));
		typeDetailsBuilder.getRegisteredImports().add(getImport(declaredByMetadataId, menuEvent));
		
		typeManagementService.generateClassFile(typeDetailsBuilder.build());
	}
	
	private MethodMetadata getMenuActionPerformedMethod(JavaType menuEvent, String declaredByMetadataId){
		JavaSymbolName methodName = new JavaSymbolName("actionPerformed");
		
		JavaType actionEvent = new JavaType("java.awt.event.ActionEvent");
		
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(new JavaType("java.lang.Override")));
		
		List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
		parameterTypes.add(new AnnotatedJavaType(actionEvent, null));
		
		List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();
		parameterNames.add(new JavaSymbolName("e"));
		
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("EventBus.publish(new " + menuEvent.getSimpleTypeName() + "(e.getActionCommand()));");
		
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, parameterTypes, parameterNames, bodyBuilder);
		
		methodBuilder.setAnnotations(annotations);
		
		return methodBuilder.build(); // Build and return a MethodMetadata instance
	}
	
	private ImportMetadata getImport(String declaredByMetadataId, JavaType javaType){

		ImportMetadataBuilder importBuilder = new ImportMetadataBuilder(declaredByMetadataId);
		importBuilder.setImportType(javaType);
		
		return importBuilder.build();
	}
	
	private String uncapitalize(String term) {
		// [ROO-1790] this is needed to adhere to the JavaBean naming conventions (see JavaBean spec section 8.8)
		return Introspector.decapitalize(StringUtils.capitalize(term));
	}

}
