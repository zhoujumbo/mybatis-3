/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.BeanWrapper;
import org.apache.ibatis.reflection.wrapper.CollectionWrapper;
import org.apache.ibatis.reflection.wrapper.MapWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;

/**
 * 对象元数据
 * 提供了对象的属性值的获得和设置等等方法
 * 😈 可以理解成，对 BaseWrapper 操作的进一步增强
 * @author Clinton Begin
 */
public class MetaObject {
  /**
   * 原始 Object 对象
   */
  private final Object originalObject;
  /**
   * 封装过的 Object 对象
   */
  private final ObjectWrapper objectWrapper;
  private final ObjectFactory objectFactory;
  private final ObjectWrapperFactory objectWrapperFactory;
  private final ReflectorFactory reflectorFactory;

  private MetaObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
    this.originalObject = object;
    this.objectFactory = objectFactory;
    this.objectWrapperFactory = objectWrapperFactory;
    this.reflectorFactory = reflectorFactory;
    // <1>根据 object 类型的不同，创建对应的 ObjectWrapper 对象
    if (object instanceof ObjectWrapper) {
      this.objectWrapper = (ObjectWrapper) object;
    } else if (objectWrapperFactory.hasWrapperFor(object)) { // <2>
      // 创建 ObjectWrapper 对象
      this.objectWrapper = objectWrapperFactory.getWrapperFor(this, object);
    } else if (object instanceof Map) {
      // 创建 MapWrapper 对象
      this.objectWrapper = new MapWrapper(this, (Map) object);
    } else if (object instanceof Collection) {
      // 创建 CollectionWrapper 对象
      this.objectWrapper = new CollectionWrapper(this, (Collection) object);
    } else {
      // 创建 BeanWrapper 对象
      this.objectWrapper = new BeanWrapper(this, object);
    }
    // <2> 处，我们可以看到 ObjectWrapperFactory 的使用，
    // 因为默认情况下的 DefaultObjectWrapperFactory 未实现任何逻辑，
    // 所以这块逻辑相当于暂时不起作用。如果想要起作用，需要自定义 ObjectWrapperFactory 的实现类。

  }

  /**
   * 创建 MetaObject 对象
   *
   * @param object 原始 Object 对象
   * @param objectFactory
   * @param objectWrapperFactory
   * @param reflectorFactory
   * @return MetaObject 对象
   */
  public static MetaObject forObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
    if (object == null) {
      // 如果 object 为空的情况下，返回 SystemMetaObject.NULL_META_OBJECT
      return SystemMetaObject.NULL_META_OBJECT;
    } else {
      return new MetaObject(object, objectFactory, objectWrapperFactory, reflectorFactory);
    }
  }

  public ObjectFactory getObjectFactory() {
    return objectFactory;
  }

  public ObjectWrapperFactory getObjectWrapperFactory() {
    return objectWrapperFactory;
  }

  public ReflectorFactory getReflectorFactory() {
    return reflectorFactory;
  }

  public Object getOriginalObject() {
    return originalObject;
  }

  public String findProperty(String propName, boolean useCamelCaseMapping) {
    return objectWrapper.findProperty(propName, useCamelCaseMapping);
  }

  public String[] getGetterNames() {
    return objectWrapper.getGetterNames();
  }

  public String[] getSetterNames() {
    return objectWrapper.getSetterNames();
  }

  public Class<?> getSetterType(String name) {
    return objectWrapper.getSetterType(name);
  }

  public Class<?> getGetterType(String name) {
    return objectWrapper.getGetterType(name);
  }

  public boolean hasSetter(String name) {
    return objectWrapper.hasSetter(name);
  }

  public boolean hasGetter(String name) {
    return objectWrapper.hasGetter(name);
  }

  /**
   * 获得指定属性的值
   * 大体逻辑上，就是不断对 name 分词，递归查找属性，直到 <1> 处，返回最终的结果。
   * 比较特殊的是，在 <2> 处，如果属性的值为 null 时，则直接返回 null ，因为值就是空的哈。
   * @param name
   * @return
   */
  public Object getValue(String name) {
    // 创建 PropertyTokenizer 对象，对 name 分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 有子表达式
    if (prop.hasNext()) {
      // 创建 MetaObject 对象
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      // <2> 递归判断子表达式 children ，获取值
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return null;
      } else {
        return metaValue.getValue(prop.getChildren());
      }

      // 无子表达式
    } else {
      // <1> 获取值
      return objectWrapper.get(prop);
    }
  }

  /**
   * 设置指定属性的指定值
   * 大体逻辑上，就是不断对 name 分词，递归查找属性，最终在 <1> 处，设置对应的值。
   * 比较特殊的是，在 <2> 处，如果属性的值为 null 时，调用 ObjectWrapper#instantiatePropertyValue(name, prop, objectFactory) 方法，
   * 创建当前 name 的 prop 属性的空对象，然后继续递归。可能有点难理解，胖友可以调试下 MetaObjectTest#shouldGetAndSetNestedMapPairUsingArraySyntax() 这个单元测试方法。
   * @param name
   * @param value
   */
  public void setValue(String name, Object value) {
    // 创建 PropertyTokenizer 对象，对 name 分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 有子表达式
    if (prop.hasNext()) {
      // 创建 MetaObject 对象
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      // 递归判断子表达式 children ，设置值
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        if (value == null) {
          // don't instantiate child path if value is null
          return;
        } else {
          // <1> 创建值
          metaValue = objectWrapper.instantiatePropertyValue(name, prop, objectFactory);
        }
      }
      // 设置值
      metaValue.setValue(prop.getChildren(), value);

      // 无子表达式
    } else {
      // <1> 设置值
      objectWrapper.set(prop, value);
    }
  }

  /**
   * 创建指定属性的 MetaObject 对象
   * @param name
   * @return
   */
  public MetaObject metaObjectForProperty(String name) {
    // 获得属性值
    Object value = getValue(name);
    // 创建 MetaObject对象
    return MetaObject.forObject(value, objectFactory, objectWrapperFactory, reflectorFactory);
  }

  public ObjectWrapper getObjectWrapper() {
    return objectWrapper;
  }

  /**
   * 判断是否为集合
   * 直接调用 objectWrapper 的对应的方法
   * @return
   */
  public boolean isCollection() {
    return objectWrapper.isCollection();
  }

  public void add(Object element) {
    objectWrapper.add(element);
  }

  public <E> void addAll(List<E> list) {
    objectWrapper.addAll(list);
  }

}
