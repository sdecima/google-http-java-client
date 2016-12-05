/*
 * Copyright (c) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.api.client.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Parses field information to determine data key name/value pair associated with the field.
 *
 * <p>
 * Implementation is thread-safe.
 * </p>
 *
 * @since 1.0
 * @author Yaniv Inbar
 */
public class FieldInfo {

  /** Cached field information. */
  private static final Map<Field, FieldInfo> CACHE = new WeakHashMap<Field, FieldInfo>();

  /**
   * Returns the field information for the given enum value.
   *
   * @param enumValue enum value
   * @return field information
   * @throws IllegalArgumentException if the enum value has no value annotation
   * @since 1.4
   */
  public static FieldInfo of(Enum<?> enumValue) {
    try {
      FieldInfo result = FieldInfo.of(enumValue.getClass().getField(enumValue.name()));
      Preconditions.checkArgument(
          result != null, "enum constant missing @Value or @NullValue annotation: %s", enumValue);
      return result;
    } catch (NoSuchFieldException e) {
      // not possible
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the field information for the given field.
   *
   * @param field field or {@code null} for {@code null} result
   * @return field information or {@code null} if the field has no {@link #name} or for {@code null}
   *         input
   */
  public static FieldInfo of(Field field) {
    if (field == null) {
      return null;
    }
    synchronized (CACHE) {
      FieldInfo fieldInfo = CACHE.get(field);
      boolean isEnumContant = field.isEnumConstant();
      if (fieldInfo == null && (isEnumContant || !Modifier.isStatic(field.getModifiers()))) {
        String fieldName;
        if (isEnumContant) {
          // check for @Value annotation
          Value value = field.getAnnotation(Value.class);
          if (value != null) {
            fieldName = value.value();
          } else {
            // check for @NullValue annotation
            NullValue nullValue = field.getAnnotation(NullValue.class);
            if (nullValue != null) {
              fieldName = null;
            } else {
              // else ignore
              return null;
            }
          }
        } else {
          // check for @Key annotation
          Key key = field.getAnnotation(Key.class);
          if (key == null) {
            // else ignore
            return null;
          }
          fieldName = key.value();
          try {
            field.setAccessible(true);
          } catch (SecurityException e) {
            // just continue
          }
        }
        if ("##default".equals(fieldName)) {
          fieldName = field.getName();
        }
        fieldInfo = new FieldInfo(field, fieldName);
        CACHE.put(field, fieldInfo);
      }
      return fieldInfo;
    }
  }

  /** Whether the field class is "primitive" as defined by {@link Data#isPrimitive(Type)}. */
  private final boolean isPrimitive;

  /** Field. */
  private final Field field;

  /**
   * Data key name associated with the field for a non-enum-constant with a {@link Key} annotation,
   * or data key value associated with the enum constant with a {@link Value} annotation or {@code
   * null} for an enum constant with a {@link NullValue} annotation.
   *
   * <p>
   * This string is interned.
   * </p>
   */
  private final String name;

  FieldInfo(Field field, String name) {
    this.field = field;
    this.name = name == null ? null : name.intern();
    isPrimitive = Data.isPrimitive(getType());
  }

  /**
   * Returns the field.
   *
   * @since 1.4
   */
  public Field getField() {
    return field;
  }

  /**
   * Returns the data key name associated with the field for a non-enum-constant with a {@link Key}
   * annotation, or data key value associated with the enum constant with a {@link Value} annotation
   * or {@code null} for an enum constant with a {@link NullValue} annotation.
   *
   * <p>
   * This string is interned.
   * </p>
   *
   * @since 1.4
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the field's type.
   *
   * @since 1.4
   */
  public Class<?> getType() {
    return field.getType();
  }

  /**
   * Returns the field's generic type, which is a class, parameterized type, generic array type, or
   * type variable, but not a wildcard type.
   *
   * @since 1.4
   */
  public Type getGenericType() {
    return field.getGenericType();
  }

  /**
   * Returns whether the field is final.
   *
   * @since 1.4
   */
  public boolean isFinal() {
    return Modifier.isFinal(field.getModifiers());
  }

  /**
   * Returns whether the field is primitive as defined by {@link Data#isPrimitive(Type)}.
   *
   * @since 1.4
   */
  public boolean isPrimitive() {
    return isPrimitive;
  }

  /**
   * Returns the value of the field in the given object instance using reflection.
   */
  public Object getValue(Object obj) {
    return getFieldValue(field, obj);
  }

  /**
   * Sets to the given value of the field in the given object instance using reflection.
   * <p>
   * If the field is final, it checks that value being set is identical to the existing value.
   */
  public void setValue(Object obj, Object value) {
    setFieldValue(field, obj, value);
  }

  /** Returns the class information of the field's declaring class. */
  public ClassInfo getClassInfo() {
    return ClassInfo.of(field.getDeclaringClass());
  }

  @SuppressWarnings("unchecked")
  public <T extends Enum<T>> T enumValue() {
    return Enum.valueOf((Class<T>) field.getDeclaringClass(), field.getName());
  }

  /**
   * Returns the value of the given field in the given object instance using reflection.
   */
  public static Object getFieldValue(Field field, Object obj) {
    if (!field.isAccessible()) {
      try {
        return getRestrictedFieldValue(field, obj);
      } catch (Exception e) {
        // just continue
      }
    }
    try {
      return field.get(obj);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Sets to the given value of the given field in the given object instance using reflection.
   * <p>
   * If the field is final, it checks that value being set is identical to the existing value.
   */
  public static void setFieldValue(Field field, Object obj, Object value) {
    if (Modifier.isFinal(field.getModifiers())) {
      Object finalValue = getFieldValue(field, obj);
      if (value == null ? finalValue != null : !value.equals(finalValue)) {
        throw new IllegalArgumentException(
            "expected final value <" + finalValue + "> but was <" + value + "> on "
                + field.getName() + " field in " + obj.getClass().getName());
      }
    } else {
      if (!field.isAccessible()) {
        try {
          setRestrictedFieldValue(field, obj, value);
          return;
        } catch (Exception e) {
          // just continue
        }
      }
      try {
        field.set(obj, value);
      } catch (SecurityException e) {
        throw new IllegalArgumentException(e);
      } catch (IllegalAccessException e) {
        throw new IllegalArgumentException(e);
      }
    }
  }

  private static List newArrayList(Object element) {
    ArrayList<Object> list = new ArrayList<Object>();
    list.add(element);
    return list;
  }

  private static Object getRestrictedFieldValue(Field field, Object obj)
      throws NoSuchMethodException {
    for (Method m : getMethods(field, obj, MethodPrefix.GET)) {
      try {
        Object result = m.invoke(obj);
        if (result != null && List.class.isAssignableFrom(field.getType())) {
          return newArrayList(result);
        }
        return result;
      } catch (Exception e) {
        // continue trying other methods
      }
    }
    throw new NoSuchMethodException();
  }

  private static Object setRestrictedFieldValue(Field field, Object obj, Object value)
      throws NoSuchMethodException {
    for (Method m : getMethods(field, obj, MethodPrefix.SET)) {
      try {
        if (value instanceof Collection && ((Collection) value).size() == 0 &&
            m.getParameterTypes().length == 1 &&
            !Collection.class.isAssignableFrom(m.getParameterTypes()[0])) {
          return m.invoke(obj, new Object[]{null});
        } else if (value instanceof Collection && ((Collection) value).size() == 1 &&
            m.getParameterTypes().length == 1 &&
            !Collection.class.isAssignableFrom(m.getParameterTypes()[0])) {
          Object colValue = ((Collection) value).iterator().next();
          return m.invoke(obj, colValue);
        } else if (value != null && !(value instanceof Collection) &&
            m.getParameterTypes().length == 1 &&
            List.class.isAssignableFrom(m.getParameterTypes()[0])) {
          return m.invoke(obj, newArrayList(value));
        } else {
          return m.invoke(obj, value);
        }
      } catch (Exception e) {
        // continue trying other methods
      }
    }
    throw new NoSuchMethodException();
  }

  private enum MethodPrefix {
    GET, SET
  }

  private final Map<MethodPrefix, List<Method>> methodCache = new WeakHashMap<MethodPrefix,
      List<Method>>();

  private static List<Method> getMethods(Field field, Object obj, MethodPrefix prefix) {
    synchronized (CACHE) {
      FieldInfo fieldInfo = FieldInfo.of(field);
      List<Method> methods = fieldInfo.methodCache.get(prefix);
      if (methods == null) {
        methods = new ArrayList<Method>();
        String methodName = prefix + field.getName();
        for (Method method : obj.getClass().getMethods()) {
          if (method.getName().compareToIgnoreCase(methodName) == 0) {
            methods.add(method);
          }
        }
        fieldInfo.methodCache.put(prefix, methods);
      }
      return methods;
    }
  }
}
