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
package org.apache.ibatis.parsing;

/**
 * Token 处理器接口
 *
 * 该接口有四个实现类
 * {@see BindingTokenParser}、{@see VariableTokenHandler}、{@see DynamicCheckerTokenParser}、{@see ParameterMappingTokenParser}
 * 其中，只有{@see VariableTokenHandler}在parsing包下和解析器有关
 * @author Clinton Begin
 */
public interface TokenHandler {
  /**
   * 处理 Token
   * 在 {@link GenericTokenParser#parse(String)}的<x> 处用到了该调用
   * @param content Token 字符串
   * @return
   */
  String handleToken(String content);
}

