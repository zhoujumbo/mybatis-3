/**
 *    Copyright 2009-2021 the original author or authors.
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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.util.MapUtil;

/**
 * 反射器
 * 每个 Reflector 对应一个类。Reflector 会缓存反射操作需要的类的信息，例如：构造方法、属性名、setting / getting 方法等等
 *
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 * @author Clinton Begin
 */
public class Reflector {
  /**
   * 所要解析的Class对象
   */
  private final Class<?> type;
  /**
   * 可读属性数组，从get或is开头的方法中截取的属性值以及字段名称，例如：getName -> name
   */
  private final String[] readablePropertyNames;
  /**
   * 可写属性集合，从set方法中截取的属性值以及字段名称，例如：setName -> name
   */
  private final String[] writablePropertyNames;
  /**
   * 属性对应的 setting 方法的映射。
   * key 为属性名称
   * value 为 Invoker 对象
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();
  /**
   * 属性对应的 getting 方法的映射。
   * key 为属性名称
   * value 为 Invoker 对象
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();
  /**
   * 属性对应的 setting 方法的方法参数类型的映射。{@link #setMethods}
   * key 为属性名称
   * value 为方法参数类型
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  /**
   * 属性对应的 getting 方法的返回值类型的映射。{@link #getMethods}
   * key 为属性名称
   * value 为返回值的类型
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();

  /**
   * 如果当前Class对象有无参构造器，则此字段为无参构造器
   * 默认无参构造方法，在 <1> 处初始化。{@link #addDefaultConstructor}
   */
  private Constructor<?> defaultConstructor;

  /**
   * 不区分大小写的属性集合
   * key为所有属性的大写方式，value为对应的属性名称，例如：NAME -> name
   */
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    // 设置对应的类
    type = clazz;
    // <1> 初始化 defaultConstructor
    addDefaultConstructor(clazz);
    // <2> // 初始化 getMethods 和 getTypes ，通过遍历 getting 方法
    addGetMethods(clazz);
    // <3> // 初始化 setMethods 和 setTypes ，通过遍历 setting 方法。
    addSetMethods(clazz);
    // <4> // 初始化 getMethods + getTypes 和 setMethods + setTypes ，通过遍历 fields 属性。
    addFields(clazz);
    // <5> 初始化 readablePropertyNames、writeablePropertyNames、caseInsensitivePropertyMap 属性
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  /**
   * 查找默认无参构造函数
   * @param clazz
   */
  private void addDefaultConstructor(Class<?> clazz) {
    // 获得所有构造方法
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    // 遍历所有构造方法，查找无参的构造方法
    Arrays.stream(constructors)
      // 判断无参的构造方法
      .filter(constructor -> constructor.getParameterTypes().length == 0)
      .findAny() // 按ifPresent中的条件查找
      .ifPresent(constructor -> this.defaultConstructor = constructor);
      // 此方法和3.5.0区别，
      // 1.集合遍历改为lambda。2.去掉了判断构造方法是否是private等修饰符的条件判断，放到了其他地方进行判断.
  }

  /**
   * 初始化 getMethods 和 getTypes
   * @param clazz
   */
  private void addGetMethods(Class<?> clazz) {
    // <1> 属性与其 getting 方法的映射。属性与其 getting 方法的映射。因为父类和子类都可能定义了相同属性的 getting 方法，所以 VALUE 会是个数组。
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    // <2> 获得所有方法
    Method[] methods = getClassMethods(clazz);
    // <3> 遍历所有方法
    Arrays.stream(methods)
      // 条件：- 参数大于 0 ，说明不是 getting 方法，忽略。 - 以 get 和 is 方法名开头，说明是 getting 方法
      // PropertyNamer#isGetter()  判断方法名称，以 get 和 is 方法名开头，说明是 getting 方法
      // m.getName() ： 获取方法名称
      .filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
      //PropertyNamer.methodToProperty(name);  // <3.3> 获得属性
      // #addMethodConflict（） //<3.4> 添加到 conflictingGetters 中
      .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));

    // <4> 解决 getting 冲突方法
    resolveGetterConflicts(conflictingGetters);
  }

  /**
   * 解决 getting 冲突
   * 最终，一个属性，只保留一个对应的方法。
   * 上述解决get方法冲突的方式主要是根据get方法的返回值进行判断，
   * 当方法名相同时，则过滤掉父类方法，将子类中的方法作为最优的方法。
   * 当解决完冲突后，将最优的get方法的先关信息保存到相关的集合中。
   * @param conflictingGetters
   */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    // 遍历每个属性，查找其最匹配的方法。因为子类可以覆写父类的方法，所以一个属性，可能对应多个 getting 方法
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null; // 最匹配的方法
      String propName = entry.getKey(); // 获取属性名
      boolean isAmbiguous = false; // 是否冲突的标志
      // 遍历每个属性中的多个方法， candidate: 候选方法
      for (Method candidate : entry.getValue()) {
        // winner 为空，说明 candidate 为最匹配的方法
        if (winner == null) {
          winner = candidate;
          continue;
        }
        // <1> 基于返回类型比较。获取当前最优方法的返回值类型
        Class<?> winnerType = winner.getReturnType();
        // 获取候选方法的返回值类型
        Class<?> candidateType = candidate.getReturnType();
        // 当最优返回值与候选返回值类型相同时
        if (candidateType.equals(winnerType)) {
          // 返回值了诶选哪个相同，应该在 getClassMethods 方法中，已经合并。所以isAmbiguous设置为true，终止循环
          if (!boolean.class.equals(candidateType)) {
            isAmbiguous = true;
            break;

            // 选择 boolean 类型的 is 方法
          } else if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }

          // 不符合选择子类
          // isAssignableFrom() 方法为native方法，表示前一个类是否为后一个类的父类
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
          // 候选返回值为最优返回值的父类，或者接口时，则说名当前最优没问题,不做任何操作
          // <1.1> 符合选择子类。因为子类可以修改放大返回值。所以出现该情况时，返回子类的该方法。
          // 例如，父类的一个方法的返回值为 List ，子类对该方法的返回值可以覆写为 ArrayList 。
        } else if (winnerType.isAssignableFrom(candidateType)) {
          // 当前最优返回值为候选返回值父类或者接口时，则进行调换
          winner = candidate;

          // <1.2> isAmbiguous设置为true，终止循环
        } else {
          isAmbiguous = true;
          break;
        }
      }
      // <2> 添加到 getMethods 和 getTypes 中
      addGetMethod(propName, winner, isAmbiguous);
    }
  }

  /**
   * 添加getter方法到getTypes中
   * 对getMethods 集合 和 getTypes 集合进行填充
   *
   * @param name 属性名
   * @param method 方法对象
   * @param isAmbiguous 是否冲突的标志
   */
  private void addGetMethod(String name, Method method, boolean isAmbiguous) {
    // 当isAmbiguous 为true时，代表发生了异常
    MethodInvoker invoker = isAmbiguous
        ? new AmbiguousMethodInvoker(method, MessageFormat.format(
            "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
            name, method.getDeclaringClass().getName()))
        : new MethodInvoker(method);
    // 添加到getMethods中
    getMethods.put(name, invoker);
    // 获取其返回值， 对于TypeParameterResolver后面会进一步进行解析
    /**  {@link TypeParameterResolver#resolveReturnType} **/
    Type returnType = TypeParameterResolver.resolveReturnType(method, type);
    // 添加到 getTypes中，typeToClass（）获得 java.lang.reflect.Type 对应的类
    getTypes.put(name, typeToClass(returnType));
  }

  /**
   * 初始化 setMethods 和 setTypes
   * @param clazz
   */
  private void addSetMethods(Class<?> clazz) {
    // 属性与其 setting 方法的映射。
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    // 获取当前类的所有方法
    Method[] methods = getClassMethods(clazz);
    // 过滤掉参数个数大于1 并且方法名不符合规范的方法
    Arrays.stream(methods)
      // 过滤掉参数个数大于1 并且方法名不符合规范的方法
      .filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
      // PropertyNamer.methodToProperty(name);获得属性
      // 添加到 conflictingSetters 中
      .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 解决Setter方法冲突
    resolveSetterConflicts(conflictingSetters);
  }

  /**
   * 将方法添加到对应的name的集合
   * @param conflictingMethods
   * @param name
   * @param method
   */
  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    // 校验参数名称
    if (isValidPropertyName(name)) {
      List<Method> list = MapUtil.computeIfAbsent(conflictingMethods, name, k -> new ArrayList<>());
      list.add(method);
    }
  }

  /**
   * 解决setter冲突
   * @param conflictingSetters
   */
  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    // 遍历每个属性，查找其最匹配的方法。因为子类可以覆写父类的方法，所以一个属性，可能对应多个 setting 方法
    for (Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
      String propName = entry.getKey(); // 获取属性名
      List<Method> setters = entry.getValue();// 获取方法
      Class<?> getterType = getTypes.get(propName);
      // 从getMethod中获取invoker, 若获取value与AmbiguousMethodInvoker 不是一个类型（应该都是Invoker类型），则说明出现冲突
      boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
      // 设置setter方法默认不是冲突的
      boolean isSetterAmbiguous = false;
      Method match = null;
      // <1> 遍历属性对应的 setting 方法
      for (Method setter : setters) {
        // 与getter对比，此处多的就是考虑了对应的 getterType 为优先级最高
        if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
          // should be the best match
          // get方法不冲突(不为null)并且set方法参数值类型与get方法返回值类型一致，此时set为 best match
          match = setter;
          break;
        }
        // 当set方法不冲突，但是返回值冲突的情况下，将选择更优的set方法
        if (!isSetterAmbiguous) {
          // 选择最有的setter方法，并设置
          match = pickBetterSetter(match, setter, propName);
          isSetterAmbiguous = match == null;
        }
      }
      if (match != null) {
        addSetMethod(propName, match);
      }
    }
  }

  /**
   * 获取最有的Setter方法类型
   * @param setter1
   * @param setter2
   * @param property
   * @return
   */
  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
        MessageFormat.format(
            "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.",
            property, setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
    setMethods.put(property, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
    setTypes.put(property, typeToClass(paramTypes[0]));
    return null;
  }

  private void addSetMethod(String name, Method method) {
    MethodInvoker invoker = new MethodInvoker(method);
    setMethods.put(name, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
    setTypes.put(name, typeToClass(paramTypes[0]));
  }

  /**
   * 获得 java.lang.reflect.Type 对应的类
   * 代码比较简单，就是寻找 Type 真正对应的类
   * @param src
   * @return
   */
  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    // 普通类型，直接使用类
    if (src instanceof Class) {
      result = (Class<?>) src;

      // 泛型类型，使用泛型
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();

      // 泛型数组，获得具体类
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) { // 普通类型
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType); // 递归该方法，返回类
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    // 都不符合，使用 Object 类
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    // 获得所有 field 们
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      // <1> 若 setMethods 不存在，添加到 setMethods 和 setTypes 中
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        int modifiers = field.getModifiers();
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      // 添加到 getMethods 和 getTypes 中
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    // 递归，处理父类
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  /**
   *
   * @param field
   */
  private void addSetField(Field field) {
    // 判断是合理的属性
    if (isValidPropertyName(field.getName())) {
      // 注意，此处创建的是 SetFieldInvoker 对象
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      // 注意，此处创建的是 GetFieldInvoker 对象
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * 校验参数名称
   * 名称不是$开头；不是serialVersionUID参数；名称不是class
   * @param name
   * @return
   */
  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * 获得所有方法
   *
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler <code>Class.getMethods()</code>,
   * because we want to look for private methods as well.
   *
   * @param clazz The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> clazz) {
    // 每个方法签名与该方法的映射
    Map<String, Method> uniqueMethods = new HashMap<>();
    // 循环类，类的父类，类的父类的父类，直到父类为 Object
    Class<?> currentClass = clazz;
    while (currentClass != null && currentClass != Object.class) {
      // <1> 记录当前类定义的方法，添加方法数组到 uniqueMethods
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      // <2> 记录接口中定义的方法
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }
      // 获得父类
      currentClass = currentClass.getSuperclass();
    }
    // 转换成 Method 数组返回
    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[0]);
  }

  /**
   * 添加方法数组到 uniqueMethods
   *
   * @param uniqueMethods
   * @param methods
   */
  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      if (!currentMethod.isBridge()) { // 忽略 bridge 方法，参见 https://www.zhihu.com/question/54895701/answer/141623158 文章
        // <3> 获得方法签名
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        // 当 uniqueMethods 不存在时，进行添加
        if (!uniqueMethods.containsKey(signature)) {
          // 添加到 uniqueMethods 中
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  /**
   * 获取方法签名
   * 格式：returnType#方法名:参数名1,参数名2,参数名3 。
   * 例如：void#checkPackageAccess:java.lang.ClassLoader,boolean 。
   * @param method
   * @return
   */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    // 返回类型
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    // 方法名
    sb.append(method.getName());
    // 方法参数
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   * 判断，是否可以修改可访问性
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.containsKey(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.containsKey(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
