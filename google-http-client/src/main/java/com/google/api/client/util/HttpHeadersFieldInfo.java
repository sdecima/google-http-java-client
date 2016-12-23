package com.google.api.client.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Sebastián Décima <sdecima@boomi.com>
 */
public class HttpHeadersFieldInfo extends FieldInfo {
  HttpHeadersFieldInfo(Field field, String name) {
    super(field, name);
  }

  private static List newArrayList(Object element) {
    ArrayList<Object> list = new ArrayList<Object>();
    list.add(element);
    return list;
  }

  protected Object getRestrictedFieldValue(Field field, Object obj)
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

  protected Object setRestrictedFieldValue(Field field, Object obj, Object value)
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
}
