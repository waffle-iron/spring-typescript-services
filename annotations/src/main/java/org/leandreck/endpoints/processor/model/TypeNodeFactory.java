/**
 * Copyright © 2016 Mathias Kowalzik (Mathias.Kowalzik@leandreck.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.leandreck.endpoints.processor.model;

import org.leandreck.endpoints.annotations.TypeScriptIgnore;
import org.leandreck.endpoints.annotations.TypeScriptType;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.toList;

/**
 * Created by Mathias Kowalzik (Mathias.Kowalzik@leandreck.org) on 28.08.2016.
 */
class TypeNodeFactory {

    private static final Map<String, String> mappings = new HashMap<>(20);
    private static final String NUMBER_TYPE = "Number";
    private static final String STRING_TYPE = "String";
    private static final String BOOLEAN_TYPE = "Boolean";

    static {
        //Void
        mappings.put("VOID", "Void");

        //Number
        mappings.put("BYTE", NUMBER_TYPE);
        mappings.put("Byte", NUMBER_TYPE);
        mappings.put("SHORT", NUMBER_TYPE);
        mappings.put("Short", NUMBER_TYPE);
        mappings.put("INT", NUMBER_TYPE);
        mappings.put("Integer", NUMBER_TYPE);
        mappings.put("LONG", NUMBER_TYPE);
        mappings.put("Long", NUMBER_TYPE);
        mappings.put("FLOAT", NUMBER_TYPE);
        mappings.put("Float", NUMBER_TYPE);
        mappings.put("DOUBLE", NUMBER_TYPE);
        mappings.put("Double", NUMBER_TYPE);
        mappings.put("BigDecimal", NUMBER_TYPE);
        mappings.put("BigInteger", NUMBER_TYPE);

        //String
        mappings.put("CHAR", STRING_TYPE);
        mappings.put("Character", STRING_TYPE);
        mappings.put("String", STRING_TYPE);

        //Boolean
        mappings.put("BOOLEAN", BOOLEAN_TYPE);
        mappings.put("Boolean", BOOLEAN_TYPE);

        //Date
        mappings.put("Date", "Date");

        //any
        mappings.put("Object", "any");
    }

    private final Types typeUtils;
    private final Elements elementUtils;

    private final Map<String, List<TypeNode>> createdChildren = new ConcurrentHashMap<>(200);

    public TypeNodeFactory(final Types typeUtils, final Elements elementUtils) {
        this.typeUtils = typeUtils;
        this.elementUtils = elementUtils;
    }

    private TypeNode createTypeNode(final VariableElement variableElement) {
        final TypeScriptType typeScriptTypeAnnotation = variableElement.getAnnotation(TypeScriptType.class);
        final TypeMirror typeMirror = variableElement.asType();
        final TypeNodeKind typeNodeKind = defineKind(typeMirror);
        final String typeName = defineName(typeMirror, typeNodeKind, typeScriptTypeAnnotation);
        final String fieldName = variableElement.getSimpleName().toString();
        final List<TypeNode> children = createdChildren.get(typeName);
        if (children != null) {
            return initType(fieldName, typeNodeKind, typeName, typeMirror, typeScriptTypeAnnotation, children);
        }
        return initType(fieldName, typeNodeKind, typeName, typeMirror, typeScriptTypeAnnotation);
    }

    public TypeNode createTypeNode(final TypeMirror typeMirror) {
        final TypeNodeKind typeNodeKind = defineKind(typeMirror);
        final String typeName = defineName(typeMirror, typeNodeKind);
        final String fieldName = "TYPE-ROOT";
        final List<TypeNode> children = createdChildren.get(typeName);
        if (children != null) {
            return initType(fieldName, typeNodeKind, typeName, typeMirror, null, children);
        }
        return initType(fieldName, typeNodeKind, typeName, typeMirror, null);
    }

    private TypeNode initType(String fieldName, TypeNodeKind typeNodeKind, String typeName, TypeMirror typeMirror, final TypeScriptType typeScriptTypeAnnotation) {
        final TypeNode newTypeNode;
        //don't traverse mapped types
        if (mappings.containsValue(typeName)) {
            newTypeNode = new TypeNode(fieldName, typeName, typeNodeKind);
        } else {
            final TypeElement typeElement = (TypeElement) typeUtils.asElement(typeMirror);
            final String template = defineTemplate(typeScriptTypeAnnotation);
            final List<String> publicGetter = definePublicGetter(typeElement);
            final List<TypeNode> children = defineChildren(typeElement, publicGetter);
            newTypeNode = new TypeNode(fieldName, typeName, template, typeNodeKind, children);
            createdChildren.put(typeName, children); //as traversing children happens in parallel we might have done useless work, who cares?
        }
        return newTypeNode;
    }

    private TypeNode initType(String fieldName, TypeNodeKind typeNodeKind, String typeName, TypeMirror typeMirror, final TypeScriptType typeScriptTypeAnnotation, List<TypeNode> children) {
        final String template = defineTemplate(typeScriptTypeAnnotation);
        return new TypeNode(fieldName, typeName, template, typeNodeKind, children);
    }

    private List<String> definePublicGetter(final TypeElement typeElement) {
        return ElementFilter.methodsIn(typeElement.getEnclosedElements()).stream()
                .filter(g -> g.getSimpleName().toString().startsWith("get") || g.getSimpleName().toString().startsWith("is"))
                .filter(g -> g.getModifiers().contains(Modifier.PUBLIC))
                .filter(g -> !g.getModifiers().contains(Modifier.ABSTRACT))//FIXME filter remaining modifiers
                .map(g -> g.getSimpleName().toString())
                .collect(toList());
    }

    private boolean filterVariableElements(final VariableElement variableElement, final List<String> publicGetter) {
        return publicGetter.stream().map(g -> g.toLowerCase().indexOf(variableElement.getSimpleName().toString().toLowerCase()) > -1)
                .reduce(false, (a, b) -> a || b);
    }

    private List<TypeNode> defineChildren(final TypeElement typeElement, final List<String> publicGetter) {
        return ElementFilter.fieldsIn(typeElement.getEnclosedElements()).stream()
                .filter(c -> c.getAnnotation(TypeScriptIgnore.class) == null)
                .filter(c -> !c.getModifiers().contains(Modifier.TRANSIENT))
                .filter(c -> filterVariableElements(c, publicGetter))
                .map(this::createTypeNode).collect(toList());
    }

    private static String defineTemplate(final TypeScriptType typeScriptTypeAnnotation) {
        final String template;
        if (typeScriptTypeAnnotation == null || typeScriptTypeAnnotation.template().isEmpty()) {
            template = "/org/leandreck/endpoints/templates/typescript/interface.ftl";
        } else {
            template = typeScriptTypeAnnotation.template();
        }
        return template;
    }

    private String defineName(final TypeMirror typeMirror, final TypeNodeKind typeNodeKind, final TypeScriptType typeScriptTypeAnnotation) {
        //check if has a annotation and a type
        if (typeScriptTypeAnnotation != null) {
            final String typeFromAnnotation = defineTypeFromAnnotation(typeScriptTypeAnnotation);
            if (!"UNDEFINED".equals(typeFromAnnotation)) {
                return typeFromAnnotation;
            }
        }

        return defineName(typeMirror, typeNodeKind);
    }

    private String defineName(final TypeMirror typeMirror, final TypeNodeKind typeNodeKind) {
        final String name;
        switch (typeNodeKind) {
            case ARRAY:
                name = defineNameFromArrayType((ArrayType) typeMirror);
                break;

            case COLLECTION:
                name = defineNameFromCollectionType((DeclaredType) typeMirror);
                break;

            case MAP:
                name = defineNameFromMapType((DeclaredType) typeMirror);
                break;

            case SIMPLE:
            default:
                name = defineNameFromSimpleType(typeMirror);
        }
        return name;
    }

    private String defineTypeFromAnnotation(final TypeScriptType annotation) {
        if (annotation != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }

        return "UNDEFINED";
    }

    private String defineNameFromSimpleType(final TypeMirror typeMirror) {
        final TypeKind kind = typeMirror.getKind();
        final String typeName;
        if (kind.isPrimitive() || TypeKind.VOID.equals(kind)) {
            typeName = mappings.get(kind.name());
        } else if (TypeKind.DECLARED.equals(kind)) {
            final String key = typeUtils.asElement(typeMirror).getSimpleName().toString();
            final String mappedValue = mappings.get(key);
            if (mappedValue == null) {
                typeName = "I" + key;
            } else {
                typeName = mappedValue;
            }
        } else {
            typeName = "UNDEFINED";
        }
        return typeName;
    }

    private String defineNameFromArrayType(final ArrayType arrayMirror) {
        final TypeMirror componentMirror = arrayMirror.getComponentType();
        return defineNameFromSimpleType(componentMirror);
    }

    private String defineNameFromCollectionType(final DeclaredType declaredType) {
        final List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();

        final TypeElement listElement;
        if (typeArguments.isEmpty()) {
            listElement = elementUtils.getTypeElement("java.lang.Object");
        } else {
            final TypeMirror genericMirror = typeArguments.get(0);
            if (TypeKind.WILDCARD.equals(genericMirror.getKind())) {
                listElement = elementUtils.getTypeElement("java.lang.Object");
            } else {
                listElement = (TypeElement) typeUtils.asElement(genericMirror);
            }
        }

        return defineNameFromSimpleType(listElement.asType());
    }

    private String defineNameFromMapType(final DeclaredType declaredType) {
        final List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();

        final TypeElement keyElement;
        final TypeElement valueElement;
        if (typeArguments.isEmpty()) {
            keyElement = elementUtils.getTypeElement("java.lang.Object");
            valueElement = elementUtils.getTypeElement("java.lang.Object");
        } else {
            final TypeMirror keyMirror = typeArguments.get(0);
            if (TypeKind.WILDCARD.equals(keyMirror.getKind())) {
                keyElement = elementUtils.getTypeElement("java.lang.Object");
            } else {
                keyElement = (TypeElement) typeUtils.asElement(keyMirror);
            }

            final TypeMirror valueMirror = typeArguments.get(1);
            if (TypeKind.WILDCARD.equals(valueMirror.getKind())) {
                valueElement = elementUtils.getTypeElement("java.lang.Object");
            } else {
                valueElement = (TypeElement) typeUtils.asElement(valueMirror);
            }
        }

        final String keyName = defineNameFromSimpleType(keyElement.asType());
        final String valueName = defineNameFromSimpleType(valueElement.asType());

        return keyName + "/" + valueName;
    }

    private TypeNodeKind defineKind(final TypeMirror typeMirror) {
        final TypeKind kind = typeMirror.getKind();
        final TypeNodeKind typeNodeKind;

        switch (kind) {
            case ARRAY:
                typeNodeKind = TypeNodeKind.ARRAY;
                break;

            case DECLARED:
                final TypeMirror collectionMirror = elementUtils.getTypeElement("java.util.Collection").asType();
                final TypeMirror mapMirror = typeUtils.getDeclaredType(elementUtils.getTypeElement("java.util.Map"));
                if (typeUtils.isAssignable(typeMirror, typeUtils.erasure(collectionMirror))) {
                    typeNodeKind = TypeNodeKind.COLLECTION;
                } else if (typeUtils.isAssignable(typeMirror, typeUtils.erasure(mapMirror))) {
                    typeNodeKind = TypeNodeKind.MAP;
                } else {
                    typeNodeKind = TypeNodeKind.SIMPLE;
                }
                break;

            case BOOLEAN:
            case BYTE:
            case CHAR:
            case DOUBLE:
            case FLOAT:
            case INT:
            case LONG:
            case SHORT:
            case VOID:
            default:
                typeNodeKind = TypeNodeKind.SIMPLE;
        }

        return typeNodeKind;
    }
}
