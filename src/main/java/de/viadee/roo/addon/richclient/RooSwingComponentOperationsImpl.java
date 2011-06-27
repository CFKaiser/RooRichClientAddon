package de.viadee.roo.addon.richclient;
import java.beans.Introspector;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.springframework.roo.classpath.PhysicalTypeCategory;
import org.springframework.roo.classpath.PhysicalTypeIdentifier;
import org.springframework.roo.classpath.PhysicalTypeMetadata;
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
import org.springframework.roo.classpath.details.annotations.AnnotationMetadata;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.classpath.itd.InvocableMemberBodyBuilder;
import org.springframework.roo.classpath.scanner.MemberDetails;
import org.springframework.roo.classpath.scanner.MemberDetailsScanner;
import org.springframework.roo.metadata.MetadataService;
import org.springframework.roo.model.DataType;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.project.Path;
import org.springframework.roo.project.ProjectOperations;
import org.springframework.roo.support.util.Assert;
import org.springframework.roo.support.util.StringUtils;

/**
 * Implementation of {@link RooSwingComponentOperations} interface.
 * 
 * @author Christian Kaiser
 * @since 1.1.1
 */
@Component
@Service
public class RooSwingComponentOperationsImpl implements
		RooSwingComponentOperations {
	
	@Reference ProjectOperations projectOperations;
	@Reference TypeLocationService typeLocationService;
	@Reference TypeManagementService typeManagementService;
	@Reference MemberDetailsScanner memberDetailsScanner;
	@Reference MetadataService metadataService;
	
	private JavaType beanAdapter = new JavaType("com.jgoodies.binding.beans.BeanAdapter");
	private JavaType valueModel = new JavaType("com.jgoodies.binding.value.ValueModel");
	private JavaType bindings = new JavaType("com.jgoodies.binding.adapter.Bindings");
	private JavaType list = new JavaType("java.util.List");
	private JavaType stringArrayOneDimensional = new JavaType("java.lang.String", 1, DataType.TYPE, null, null);
	private JavaType objectArrayTwoDimensional = new JavaType("java.lang.Object", 2, DataType.TYPE, null, null);
	
	/**
	 * {@inheritDoc}
	 */
	public void createRooComponents(){
		createRooPanelClass();
		createRooTableClassesForAllEntites();
		createRooButtonClass();
		createRooTextFieldClass();
		createRooTableModelClass();
	}
	
	/**
	 * Creates a RooTableModel class for the RooTable Component.  Implements AbstractTableModel.
	 */
	private void createRooTableModelClass(){
		JavaType rooTableModel = new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".rooswingcomponents.RooTableModel");
		String resourceIdentifier = typeLocationService.getPhysicalLocationCanonicalPath(rooTableModel, Path.SRC_MAIN_JAVA);
		String declaredByMetadataId = PhysicalTypeIdentifier.createIdentifier(rooTableModel, projectOperations.getPathResolver().getPath(resourceIdentifier));
		ClassOrInterfaceTypeDetailsBuilder typeDetailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(declaredByMetadataId, Modifier.PUBLIC, rooTableModel, PhysicalTypeCategory.CLASS);
		
		JavaType abstractTableModel = new JavaType("javax.swing.table.AbstractTableModel");
		
		typeDetailsBuilder.addField(getDataFieldForRooTableModelClass(declaredByMetadataId));
		typeDetailsBuilder.addField(getColumnNamesFieldForRooTableModelClass(declaredByMetadataId));
		typeDetailsBuilder.addConstructor(getConstructorForRooTableModelClass(declaredByMetadataId));
		typeDetailsBuilder.addMethod(getGetRowCountMethodForRooTableModelClass(declaredByMetadataId));
		typeDetailsBuilder.addMethod(getGetColumnCountMethodForRooTableModelClass(declaredByMetadataId));
		typeDetailsBuilder.addMethod(getGetValueAtMethodForRooTableModelClass(declaredByMetadataId));
		typeDetailsBuilder.addMethod(getGetColumnNameMethodForRooTableModelClass(declaredByMetadataId));
		typeDetailsBuilder.addExtendsTypes(abstractTableModel);
		
		generateClassFile(typeDetailsBuilder);
	}
	
	/**
	 * Generates a getColumnName method for the RooTableModel class.
	 * 
	 * @param String declaredByMetadataId
	 * @return new MethodMetadata
	 */
	private MethodMetadata getGetColumnNameMethodForRooTableModelClass(String declaredByMetadataId) {
		JavaSymbolName methodName = new JavaSymbolName("getColumnName");
		JavaType returnType = new JavaType("java.lang.String");
		
		List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
		parameterTypes.add(new AnnotatedJavaType(new JavaType("java.lang.Integer"), null));
		
		JavaSymbolName col = new JavaSymbolName("col");
		List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();
		parameterNames.add(col);
		
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("return columnNames[col];");
		
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, returnType, parameterTypes, parameterNames, bodyBuilder);
		
		methodBuilder.addAnnotation(getOverrideAnnotation(declaredByMetadataId));
		
		return methodBuilder.build();
	}

	/**
	 * Generates a getValueAt method for the RooTableModel class.
	 * 
	 * @param String declaredByMetadataId
	 * @return new MethodMetadata
	 */
	private MethodMetadata getGetValueAtMethodForRooTableModelClass(String declaredByMetadataId) {
		JavaSymbolName methodName = new JavaSymbolName("getValueAt");
		JavaType returnType = new JavaType("java.lang.Object");
		
		List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
		parameterTypes.add(new AnnotatedJavaType(new JavaType("java.lang.Integer"), null));
		parameterTypes.add(new AnnotatedJavaType(new JavaType("java.lang.Integer"), null));
		
		JavaSymbolName rowIndex = new JavaSymbolName("rowIndex");
		JavaSymbolName columnIndex = new JavaSymbolName("columnIndex");
		List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();
		parameterNames.add(rowIndex);
		parameterNames.add(columnIndex);
		
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("return data[rowIndex][columnIndex];");
		
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, returnType, parameterTypes, parameterNames, bodyBuilder);
		
		methodBuilder.addAnnotation(getOverrideAnnotation(declaredByMetadataId));
		
		return methodBuilder.build();
	}

	/**
	 * Generates a getColumnCount method for the RooTableModel class.
	 * 
	 * @param String declaredByMetadataId
	 * @return new MethodMetadata
	 */
	private MethodMetadata getGetColumnCountMethodForRooTableModelClass(String declaredByMetadataId) {
		JavaSymbolName methodName = new JavaSymbolName("getColumnCount");
		JavaType returnType = new JavaType("java.lang.Integer");
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("return columnNames.length;");
		
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, returnType, null, null, bodyBuilder);
		
		methodBuilder.addAnnotation(getOverrideAnnotation(declaredByMetadataId));
		
		return methodBuilder.build();
	}

	/**
	 * Generates a getRowCount method for the RooTableModel class.
	 * 
	 * @param String declaredByMetadataId
	 * @return new MethodMetadata
	 */
	private MethodMetadata getGetRowCountMethodForRooTableModelClass(String declaredByMetadataId) {
		JavaSymbolName methodName = new JavaSymbolName("getRowCount");
		JavaType returnType = new JavaType("java.lang.Integer");
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("return data.length;");
		
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, returnType, null, null, bodyBuilder);
		
		methodBuilder.addAnnotation(getOverrideAnnotation(declaredByMetadataId));
		
		return methodBuilder.build();
	}

	/**
	 * Generates a constructor for the RooTableModel class.
	 * 
	 * @param String declaredByMetadataId
	 * @return new ConstructorMetadata
	 */
	private ConstructorMetadata getConstructorForRooTableModelClass(String declaredByMetadataId) {
		ConstructorMetadataBuilder constructorBuilder = new ConstructorMetadataBuilder(declaredByMetadataId);
		
		List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
		parameterTypes.add(new AnnotatedJavaType(stringArrayOneDimensional, null));
		parameterTypes.add(new AnnotatedJavaType(objectArrayTwoDimensional, null));
		
		JavaSymbolName columnNames = new JavaSymbolName("columnNames");
		JavaSymbolName data = new JavaSymbolName("columnNames");
		List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();
		parameterNames.add(columnNames);
		parameterNames.add(data);
		
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("this." + columnNames + " = " + columnNames + ";");
		bodyBuilder.appendFormalLine("this." + data + " = " + data + ";");
		
		constructorBuilder.setParameterTypes(parameterTypes);
		constructorBuilder.setParameterNames(parameterNames);
		constructorBuilder.setBodyBuilder(bodyBuilder);
		
		return constructorBuilder.build();
	}

	/**
	 * Generates a data[][] field for the RooTableModel class.
	 * 
	 * @param String declaredByMetadataId
	 * @return new FieldMetadata
	 */
	private FieldMetadata getDataFieldForRooTableModelClass(String declaredByMetadataId) {
		JavaSymbolName fieldName = new JavaSymbolName("data");
		FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(declaredByMetadataId, Modifier.PRIVATE, fieldName, objectArrayTwoDimensional, null);
		return fieldBuilder.build();
	}
	
	/**
	 * Generates a coumnNames[] field for the RooTableModel class.
	 * 
	 * @param String declaredByMetadataId
	 * @return new FieldMetadata
	 */
	private FieldMetadata getColumnNamesFieldForRooTableModelClass(String declaredByMetadataId) {
		JavaSymbolName fieldName = new JavaSymbolName("columnNames");
		FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(declaredByMetadataId, Modifier.PRIVATE, fieldName, stringArrayOneDimensional, null);
		return fieldBuilder.build();
	}

	/**
	 * Creates a RooPanel class for the RooTable Component. Implements JPanel.
	 */
	private void createRooPanelClass(){
		JavaType rooTable = new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".rooswingcomponents.RooPanel");
		String resourceIdentifier = typeLocationService.getPhysicalLocationCanonicalPath(rooTable, Path.SRC_MAIN_JAVA);
		String declaredByMetadataId = PhysicalTypeIdentifier.createIdentifier(rooTable, projectOperations.getPathResolver().getPath(resourceIdentifier));
		ClassOrInterfaceTypeDetailsBuilder typeDetailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(declaredByMetadataId, Modifier.PUBLIC, rooTable, PhysicalTypeCategory.CLASS);
		
		typeDetailsBuilder.addField(getComponentField(declaredByMetadataId));
		
		generateClassFile(typeDetailsBuilder);
	}
	
	/**
	 * Generates panelComponent Field for RooPanel class.
	 * 
	 * @param String declaredByMetadataId
	 * @return new FieldMetadata
	 */
	private FieldMetadata getComponentField(String declaredByMetadataId){
		JavaSymbolName fieldName = new JavaSymbolName("panelComponents");
		
		FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(declaredByMetadataId, Modifier.PRIVATE, fieldName, list, null);
		return fieldBuilder.build();
	}
	
	/**
	 * Triggers the creation of RooTable classes for all entities.
	 */
	private void createRooTableClassesForAllEntites(){
		Set<ClassOrInterfaceTypeDetails> cids = typeLocationService.findClassesOrInterfaceDetailsWithAnnotation(new JavaType("org.springframework.roo.addon.entity.RooEntity"));
		for (ClassOrInterfaceTypeDetails cid : cids) {
			if (Modifier.isAbstract(cid.getModifier())) {
				continue;
			}
			JavaType entity = cid.getName();
			
			createRooTableClassForSingleEntity(entity);
		}
	}

	/**
	 * Creates a RooTable class for a single entity. Implements JXTable.
	 */
	private void createRooTableClassForSingleEntity(JavaType entity){
		JavaType rooTable = new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".rooswingcomponents.RooTable" + entity.getSimpleTypeName());
		String resourceIdentifier = typeLocationService.getPhysicalLocationCanonicalPath(rooTable, Path.SRC_MAIN_JAVA);
		String declaredByMetadataId = PhysicalTypeIdentifier.createIdentifier(rooTable, projectOperations.getPathResolver().getPath(resourceIdentifier));
		ClassOrInterfaceTypeDetailsBuilder typeDetailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(declaredByMetadataId, Modifier.PUBLIC, rooTable, PhysicalTypeCategory.CLASS);
		
		typeDetailsBuilder.getRegisteredImports().add(getImport(declaredByMetadataId, list));
		
		typeDetailsBuilder.addField(getLoggerField(declaredByMetadataId));
		typeDetailsBuilder.addConstructor(getRooTableConstructor(declaredByMetadataId, entity));
		typeDetailsBuilder.addMethod(getGetAllEntriesAsListMethod(declaredByMetadataId, entity));
		
		generateClassFile(typeDetailsBuilder);
	}
	
	/**
	 * Generates the constructor of RooTable class.
	 * @return new ConstructorMetadata
	 */
	private ConstructorMetadata getRooTableConstructor(String declaredByMetadataId, JavaType entity) {
		AnnotatedJavaType parameterType = new AnnotatedJavaType(entity, null);
		
		JavaType list = new JavaType("java.util.List", 0, DataType.TYPE, null, Arrays.asList(entity));
		
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(list.getNameIncludingTypeParameters() + " entityList = getEntityList(entity);");
		bodyBuilder.appendFormalLine(objectArrayTwoDimensional.getNameIncludingTypeParameters() + " tableData = convertToObjectArray(entityList);");
		bodyBuilder.appendFormalLine(stringArrayOneDimensional.getNameIncludingTypeParameters() + " columnNames = getColumnNames(entity);");
		bodyBuilder.appendFormalLine("this.setModel(new RooTableModel(tableData, columnNames));");
		
		ConstructorMetadataBuilder constructorBuilder = new ConstructorMetadataBuilder(declaredByMetadataId);
		
		constructorBuilder.addParameterType(parameterType);
		constructorBuilder.addParameterName(new JavaSymbolName(uncapitalize(entity.getSimpleTypeName())));
		
		constructorBuilder.setModifier(Modifier.PUBLIC);
		constructorBuilder.setBodyBuilder(bodyBuilder);
		return constructorBuilder.build();
	}
	
	/**
	 * Generates the getEntityList method for RooTable class.
	 * 
	 * @param declaredByMetadataId
	 * @return methodMetadata
	 */
	private MethodMetadata getGetAllEntriesAsListMethod(String declaredByMetadataId, JavaType entity) {
		String findAllMethodName = getFindAllMethodNameForEntity(entity);
		
		JavaType list = new JavaType("java.util.List", 0, DataType.TYPE, null, Arrays.asList(entity));
		
		JavaSymbolName methodName = new JavaSymbolName("getAllEntriesAsList");
		
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("return " + entity.getSimpleTypeName() + "." + findAllMethodName + "();");
		
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(declaredByMetadataId, Modifier.PUBLIC, methodName, list , bodyBuilder);
		methodBuilder.setReturnType(list);
		return methodBuilder.build();
	}

	/**
	 * Creates a RooButton class. Implements JButton.
	 */
	private void createRooButtonClass(){
		JavaType rooButton = new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".rooswingcomponents.RooButton");
		String resourceIdentifier = typeLocationService.getPhysicalLocationCanonicalPath(rooButton, Path.SRC_MAIN_JAVA);
		String declaredByMetadataId = PhysicalTypeIdentifier.createIdentifier(rooButton, projectOperations.getPathResolver().getPath(resourceIdentifier));
		ClassOrInterfaceTypeDetailsBuilder typeDetailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(declaredByMetadataId, Modifier.PUBLIC, rooButton, PhysicalTypeCategory.CLASS);
		
		JavaType extendsType = new JavaType("javax.swing.JButton");
		
		typeDetailsBuilder.addExtendsTypes(extendsType);
		typeDetailsBuilder.addConstructor(getConstructorForRooButtonClass(declaredByMetadataId));
		
		generateClassFile(typeDetailsBuilder);
	}
	
	/**
	 * Generates a constructor for RooButton class.
	 * 
	 * @param String declaredByMetadataId
	 * @return new ConstructorMetadata
	 */
	private ConstructorMetadata getConstructorForRooButtonClass(String declaredByMetadataId) {
		AnnotatedJavaType listener = new AnnotatedJavaType(new JavaType("java.awt.event.ActionListener"), null);
		
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("this.addActionListener(listener);");
		
		ConstructorMetadataBuilder constructorBuilder = new ConstructorMetadataBuilder(declaredByMetadataId);
		
		constructorBuilder.addParameterType(listener);
		constructorBuilder.addParameterName(new JavaSymbolName("listener"));
		
		constructorBuilder.setModifier(Modifier.PUBLIC);
		constructorBuilder.setBodyBuilder(bodyBuilder);
		
		return constructorBuilder.build();
	}
	
	/**
	 * Creates a RooButton class. Implements JTextField.
	 */
	private void createRooTextFieldClass(){
		JavaType rooTextField = new JavaType(projectOperations.getProjectMetadata().getTopLevelPackage() + ".rooswingcomponents.RooTextField");
		String resourceIdentifier = typeLocationService.getPhysicalLocationCanonicalPath(rooTextField, Path.SRC_MAIN_JAVA);
		String declaredByMetadataId = PhysicalTypeIdentifier.createIdentifier(rooTextField, projectOperations.getPathResolver().getPath(resourceIdentifier));
		ClassOrInterfaceTypeDetailsBuilder typeDetailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(declaredByMetadataId, Modifier.PUBLIC, rooTextField, PhysicalTypeCategory.CLASS);
		
		typeDetailsBuilder.getRegisteredImports().add(getImport(declaredByMetadataId, beanAdapter));
		typeDetailsBuilder.getRegisteredImports().add(getImport(declaredByMetadataId, bindings));
		typeDetailsBuilder.getRegisteredImports().add(getImport(declaredByMetadataId, valueModel));
		
		JavaType extendsType = new JavaType("javax.swing.JTextField");
		
		typeDetailsBuilder.addExtendsTypes(extendsType);
		typeDetailsBuilder.addConstructor(getConstructorForRooTextFieldClass(declaredByMetadataId));

		generateClassFile(typeDetailsBuilder);
	}

	/**
	 * Generates a constructor for RooTextfield class.
	 * 
	 * @param String declaredByMetadataId
	 * @return new ConstructorMetadata
	 */
	private ConstructorMetadata getConstructorForRooTextFieldClass(String declaredByMetadataId) {
		List<AnnotatedJavaType> parameterTypes = new ArrayList<AnnotatedJavaType>();
		parameterTypes.add(new AnnotatedJavaType(new JavaType("java.lang.Object"), null));
		parameterTypes.add(new AnnotatedJavaType(new JavaType("java.lang.String"), null));
		
		List<JavaSymbolName> parameterNames = new ArrayList<JavaSymbolName>();
		parameterNames.add(new JavaSymbolName("entity"));
		parameterNames.add(new JavaSymbolName("propertyName"));
		
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(beanAdapter + "<Object> myBeanAdapter = new BeanAdapter<Object>((Object) entity, true);");
		bodyBuilder.appendFormalLine(valueModel + " valueModel = myBeanAdapter.getValueModel(propertyName);");
		bodyBuilder.appendFormalLine(bindings + ".bind(this, valueModel);");
		
		ConstructorMetadataBuilder constructorBuilder = new ConstructorMetadataBuilder(declaredByMetadataId);
		
		constructorBuilder.setParameterTypes(parameterTypes);
		constructorBuilder.setParameterNames(parameterNames);
		
		constructorBuilder.setModifier(Modifier.PUBLIC);
		constructorBuilder.setBodyBuilder(bodyBuilder);
		return constructorBuilder.build();
	}

	
	private String getFindAllMethodNameForEntity(JavaType entity){
		MemberDetails memberDetails = getMemberDetails(entity);
		
		String findAllMethodName = null;
		
		Assert.notEmpty(MemberFindingUtils.getMethods(memberDetails));
		for(MethodMetadata methodMetadata : MemberFindingUtils.getMethods(memberDetails)){
			JavaSymbolName methodName = new JavaSymbolName(methodMetadata.getMethodName().getSymbolName());
			String name = methodName.getSymbolName();
			
			if(name.startsWith("findAll")){
				findAllMethodName = name;
			}
		}
		return findAllMethodName;
	}
	
	private MethodMetadata getRooTableConvertToObjectArrayMethod(String declaredByMetadataId, JavaType entity){
		JavaSymbolName methodName = new JavaSymbolName("convertToObjectArray");
		
		
		
		return null;
	}
	
	private MethodMetadata getRooTableGetColumnNamesMethod(String declaredByMetadataId, JavaType entity){
		JavaSymbolName methodName = new JavaSymbolName("getColumnNames");
		
		
		
		return null;
	}

	

	/**
	 * Generates a logger field.
	 * @param declaredByMetadataId
	 * @return fieldMetaData
	 */
	private FieldMetadata getLoggerField(String declaredByMetadataId) {
		JavaSymbolName fieldName = new JavaSymbolName("log");
		JavaType type = new JavaType("java.util.logging.Logger");
		FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(declaredByMetadataId, Modifier.PRIVATE, fieldName, type, null);
		return fieldBuilder.build();
	}
	
	/**
	 * Generates a class file from a typeDetailsBuilder.
	 * @param typeDetailsBuilder
	 */
	private void generateClassFile(ClassOrInterfaceTypeDetailsBuilder typeDetailsBuilder){
		typeManagementService.generateClassFile(typeDetailsBuilder.build());
	}
	
	private ImportMetadata getImport(String declaredByMetadataId, JavaType javaType){

		ImportMetadataBuilder importBuilder = new ImportMetadataBuilder(declaredByMetadataId);
		importBuilder.setImportType(javaType);
		
		return importBuilder.build();
	}
	
	public MemberDetails getMemberDetails(JavaType javaType) {
		PhysicalTypeMetadata physicalTypeMetadata = (PhysicalTypeMetadata) metadataService.get(PhysicalTypeIdentifier.createIdentifier(javaType, Path.SRC_MAIN_JAVA));
		Assert.notNull(physicalTypeMetadata, "Unable to obtain physical type metdata for type " + javaType.getFullyQualifiedTypeName());
		ClassOrInterfaceTypeDetails classOrInterfaceDetails = (ClassOrInterfaceTypeDetails) physicalTypeMetadata.getMemberHoldingTypeDetails();
		return memberDetailsScanner.getMemberDetails(RooSwingComponentOperationsImpl.class.getName(), classOrInterfaceDetails);
	}
	
	private String uncapitalize(String term) {
		// [ROO-1790] this is needed to adhere to the JavaBean naming conventions (see JavaBean spec section 8.8)
		return Introspector.decapitalize(StringUtils.capitalize(term));
	}
	

	private AnnotationMetadata getOverrideAnnotation(String declaredByMetadataId) {
		AnnotationMetadataBuilder annotationBuilder = new AnnotationMetadataBuilder(new JavaType("java.lang.Override"));
		return annotationBuilder.build();
	}


}
