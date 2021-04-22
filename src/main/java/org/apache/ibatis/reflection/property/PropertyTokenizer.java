/**
 *    Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * 实现 Iterator 接口，属性分词器，支持迭代器的访问方式
 * 举个例子，在访问 "order[0].item[0].name" 时，
 * 我们希望拆分成 "order[0]"、"item[0]"、"name" 三段，那么就可以通过 PropertyTokenizer 来实现。
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
  /**
   * 当前字符串
   */
  private String name;
  /**
   * 索引的 {@link #name} ，因为 {@link #name} 如果存在 {@link #index} 会被更改
   */
  private final String indexedName;
  /**
   * 编号。
   * 分成两种情况：
   * 对于数组 name[0] ，则 index = 0
   * 对于 Map map[key] ，则 index = key
   */
  private String index;
  /**
   * 剩余字符串
   */
  private final String children;

  public PropertyTokenizer(String fullname) {
    // <1> 初始化 name、children 字符串，使用 . 作为分隔
    int delim = fullname.indexOf('.');
    if (delim > -1) {
      name = fullname.substring(0, delim);
      children = fullname.substring(delim + 1);
    } else {
      name = fullname;
      children = null;
    }
    // <2> 记录当前 name
    indexedName = name;
    // <3> 若存在 [ ，则获得 index ，并修改 name 。
    delim = name.indexOf('[');
    if (delim > -1) {
      index = name.substring(delim + 1, name.length() - 1);
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  /**
   * 判断是否有下一个元素
   * @return
   */
  @Override
  public boolean hasNext() {
    return children != null;
  }

  /**
   * 迭代获得下一个 PropertyTokenizer 对象
   * @return
   */
  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
