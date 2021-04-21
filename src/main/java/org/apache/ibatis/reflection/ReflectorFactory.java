/**
 *    Copyright 2009-2015 the original author or authors.
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

/**
 * Reflector 工厂接口，用于创建和缓存 Reflector 对象。
 */
public interface ReflectorFactory {
  /**
   * @return 是否缓存 Reflector 对象
   * 运行时，一个类的结构是不变的，所以不需要对一个Class对象进行反复解析
   * 所以可以对一个解析过的Class对象进行缓存
   */
  boolean isClassCacheEnabled();
  /**
   * 设置是否缓存 Reflector 对象
   * @param classCacheEnabled 是否缓存
   */
  void setClassCacheEnabled(boolean classCacheEnabled);
  /**
   * 根据指定的Class对象找到对应的反射器Reflector
   * @param type 指定类
   * @return Reflector 对象
   */
  Reflector findForClass(Class<?> type);
}
