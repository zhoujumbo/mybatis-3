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
package org.apache.ibatis.reflection.invoker;

import java.lang.reflect.InvocationTargetException;

/**
 * 调用者接口
 * Invoker的设计是设计模式中的是适配器模式，适配了Method和Filed的执行。
 * 主要有三个实现类：
 * MethodInvoker:封装类Method对象，方法内部调用的就是Method.invoke(Object,Object...)方法
 * GetFieldInvoker:封装类Field对象，用于获取字段的值，调用的是Filed.get(Object)
 * SetFieldInvoker:封装类Field对象，用于设置字段的值，调用的是Filed.set(Object,Object)
 * 特殊的实现
 * AmbiguousMethodInvoker意思是模棱两可的，执行invoke会抛异常
 * @author Clinton Begin
 */
public interface Invoker {
  /**
   * 核心方法，执行一次调用。而具体调用什么方法，由子类来实现。
   * @param target 目标 要执行的目标对象
   * @param args 参数
   * @return 结果
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   */
  Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException;

  Class<?> getType();
}
